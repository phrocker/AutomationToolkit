package io.dataguardians.automation.auditing;

import java.lang.reflect.InvocationTargetException;
import java.security.GeneralSecurityException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.dataguardians.model.auditing.Rule;
import io.dataguardians.model.security.zt.JITReason;
import io.dataguardians.model.security.zt.JITRequest;
import io.dataguardians.repository.security.zt.JITServiceLocator;
import io.dataguardians.security.JITUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RuleAlertAuditor extends BaseAuditor {

  public List<AuditorRule> synchronousRules = new ArrayList<>();

  public List<AuditorRule> asyncRules = new ArrayList<>();

  final ExecutorService executorService;

  private AsyncRuleAuditorRunner runner;

  List<String> commands = new ArrayList<>();

  public static final String RECORD = "record";
  public static final String STOP = "stop";

  final Recorder recordingStudio;

  public RuleAlertAuditor(Long userId, Long sessionId, Long systemId) {
    this(userId, sessionId, systemId, null);
  }

  public RuleAlertAuditor(
      Long userId, Long sessionId, Long systemId, Recorder recordingStudio) {

    super(userId, sessionId, systemId);

    this.recordingStudio = recordingStudio;

    // async thread evaluate
    executorService = Executors.newFixedThreadPool(1);
  }

  @Override
  protected void onPartial() {
    // explicitly approved command
    if (currentTrigger.getAction() == TriggerAction.APPROVE_ACTION) {
      return;
    }

    if (currentTrigger.getAction() == TriggerAction.DENY_ACTION) {
      return;
    }
    final String cstr = get();
    final String sanitized = getSantized();
    for (AuditorRule rule : synchronousRules) {
      Optional<Trigger> result = rule.trigger(rule.requiresSanitized() ? sanitized : cstr);
      if (result.isPresent()) {
        Trigger trg = result.get();
        switch (trg.getAction()) {
          case WARN_ACTION:
            ShellAuditable.addWarning(trg);
            break;
          case JIT_ACTION:
            ShellAuditable.addJIT(trg);
            currentTrigger = trg;
            break;
          case DENY_ACTION:
            ShellAuditable.addWarning(trg);
            currentTrigger = trg;
            break;
          default:
            break;
        }
      }
    }
    runner.enqueue(cstr);
    // do nothing
  }

  public void setSynchronousRules(List<Rule> synchronousRules)
      throws ClassNotFoundException,
          NoSuchMethodException,
          InvocationTargetException,
          InstantiationException,
          IllegalAccessException {
    for (Rule rule : synchronousRules) {
      Class<? extends AuditorRule> newRuleClass =
          Class.forName(rule.getRuleClass()).asSubclass(AuditorRule.class);
      AuditorRule newRule = newRuleClass.getConstructor().newInstance();
      newRule.configure(rule.getRuleConfig());
      switch (newRule.describeAction()) {
        case JIT_ACTION:
          this.synchronousRules.add(newRule);
          this.asyncRules.add(newRule);
          break;
        case DENY_ACTION:
          this.synchronousRules.add(newRule);
          break;
        case APPROVE_ACTION:
          this.synchronousRules.add(newRule);
          break;
        default:
          this.asyncRules.add(newRule);
      }
    }
    runner = new AsyncRuleAuditorRunner(asyncRules, userId, systemId);
    executorService.submit(runner);
  }

  public void addRule(AuditorRule rule) {
    this.synchronousRules.add(rule);
  }

  @Override
  public void shutdown() {
    super.shutdown();
    // nothing to do here
    executorService.shutdownNow();
  }

  private static final class AsyncRuleAuditorRunner implements Runnable {

    public AtomicBoolean running = new AtomicBoolean(false);

    public final List<AuditorRule> asyncRules;

    LinkedBlockingDeque<String> stringsToReview;

    Long userId;
    Long systemId;

    public AsyncRuleAuditorRunner(List<AuditorRule> asyncRules, Long userId, Long systemId) {
      this.userId = userId;
      this.systemId = systemId;
      this.stringsToReview = new LinkedBlockingDeque<>();
      this.asyncRules = asyncRules;
      running.set(true);
    }

    public void enqueue(String cstr) {
      stringsToReview.add(cstr);
    }

    @Override
    public void run() {
      while (running.get()) {
        try {

          String nextstr = stringsToReview.poll(100, TimeUnit.MILLISECONDS);
          if (null == nextstr) continue;
          for (AuditorRule rule : asyncRules) {
            Optional<Trigger> result = rule.trigger(nextstr);
            if (result.isPresent()) {
              Trigger trg = result.get();
              switch (trg.getAction()) {
                case WARN_ACTION:
                  ShellAuditable.addWarning(trg);
                  break;
                case JIT_ACTION:
                  if (!JITUtils.isApproved(nextstr, userId, systemId)) {
                    ShellAuditable.addJIT(trg);
                  }
                  break;
                default:
                  break;
              }
            }
          }
        } catch (InterruptedException | SQLException | GeneralSecurityException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public synchronized String clear(int keycode) {

    if (keycode == 13 && currentTrigger.getAction() == TriggerAction.DENY_ACTION) {
      log.debug("no change");
    } else if (keycode == 13 && currentTrigger.getAction() == TriggerAction.RECORD_ACTION) {
      log.debug("&** record no change");
    } else {
      currentTrigger = Trigger.NO_ACTION;
    }
    return super.clear(keycode);
  }

  private boolean isRestrictedCommand() {
    return currentTrigger.getAction() == TriggerAction.JIT_ACTION
        || currentTrigger.getAction() == TriggerAction.DENY_ACTION;
  }

  @Override
  protected synchronized TriggerAction submit(String command) {
    // currentTrigger

    if (null != recordingStudio && !isRestrictedCommand()) {

      TriggerAction action = recordingStudio.submit(command);
      if (action == TriggerAction.RECORD_ACTION) {
        currentTrigger = Trigger.RECORD_ACTION;
        return TriggerAction.RECORD_ACTION;
      } else if (recordingStudio.isRecordingStarted()) {
        return TriggerAction.NO_ACTION;
      }
    }

    if (currentTrigger.getAction() == TriggerAction.JIT_ACTION) {
      // need to form a jit request
      try {
        if (!JITUtils.isApproved(command, userId, systemId)) {
          if (!JITServiceLocator.getRepository().hasJITRequest(command, userId, systemId)) {
            JITReason reason = JITUtils.createReason("need ", " ticket ", " url");
            JITRequest request = JITUtils.createRequest(command, reason, userId, systemId);
            request = JITServiceLocator.getRepository().addJITRequest(request);
          }

          // keep the current trigger
        } else {
          currentTrigger = Trigger.NO_ACTION;
        }

      } catch (SQLException e) {
        log.error("error while evaluating jit action", e);
        throw new RuntimeException(e);
      } catch (GeneralSecurityException e) {
        log.error("error while evaluating jit action", e);
        throw new RuntimeException(e);
      } catch (Throwable t) {
        log.error("error while evaluating jit action", t);
        throw new RuntimeException(t.getMessage());
      }

    } else {
      log.trace("on message {}", command);
    }
    return currentTrigger.getAction();
  }
}
