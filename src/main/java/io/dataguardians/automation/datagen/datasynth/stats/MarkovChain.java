/* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package io.dataguardians.automation.datagen.datasynth.stats;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import com.google.common.base.Splitter;

/**
 * Adapted from Open source code found here: https://rosettacode.org/wiki/Markov_chain_text_generator#Java
 */
public class MarkovChain {
    private static final Random r = new Random();

    Map<String, List<String>> markovDict = new HashMap<>();

    private int keySize = 3;

    public MarkovChain(InputStream seedStream, InputStream... streams) throws IOException {
        this(seedStream, 3, streams);
    }

    public MarkovChain(InputStream seedStream, int keySize, InputStream... streams) throws IOException {
        this.keySize = keySize;
        loadDocument(seedStream);
        for (InputStream stream : streams) {
            loadDocument(stream);
        }

    }

    private void loadDocument(InputStream stream) throws IOException {
        final String outputString = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        List<String> words = new ArrayList<>();
        Splitter.on(' ').split(outputString).forEach(words::add);
        for (int i = 0; i < (words.size() - keySize); ++i) {
            StringBuilder key = new StringBuilder(words.get(i));
            for (int j = i + 1; j < i + keySize; ++j) {
                key.append(' ').append(words.get(j));
            }
            String value = (i + keySize < words.size()) ? words.get(i + keySize) : "";
            if (!markovDict.containsKey(key.toString())) {
                ArrayList<String> list = new ArrayList<>();
                list.add(value);
                markovDict.put(key.toString(), list);
            } else {
                markovDict.get(key.toString()).add(value);
            }
        }
    }

    /**
     * Adapted from open source code.
     *
     * @param expectedSize
     *
     * @return
     *
     * @throws IOException
     */
    public String produce(int expectedSize) throws IOException {
        int n = 0;
        int rn = r.nextInt(markovDict.size());
        String prefix = (String) markovDict.keySet().toArray()[rn];
        List<String> output = new ArrayList<>(Arrays.asList(prefix.split(" ")));

        while (true) {
            List<String> suffix = markovDict.get(prefix);
            if (suffix.size() == 1) {
                if (Objects.equals(suffix.get(0), ""))
                    return output.stream().reduce("", (a, b) -> a + " " + b);
                output.add(suffix.get(0));
            } else {
                rn = r.nextInt(suffix.size());
                output.add(suffix.get(rn));
            }
            if (output.size() >= expectedSize)
                return output.stream().limit(expectedSize).reduce("", (a, b) -> a + " " + b);
            n++;
            prefix = output.stream().skip(n).limit(keySize).reduce("", (a, b) -> a + " " + b).trim();
        }
    }

    /**
     * Adapted from open source code.
     *
     * @param expectedSize
     *
     * @return
     *
     * @throws IOException
     */
    public String produceText(int expectedSize) throws IOException {
        int n = 0;
        int rn = r.nextInt(markovDict.size());
        String prefix = (String) markovDict.keySet().toArray()[rn];
        List<String> output = new ArrayList<>(Arrays.asList(prefix.split(" ")));
        long totalSize = 0;
        while (true) {
            List<String> suffix = markovDict.get(prefix);
            if (suffix.size() == 1) {
                if (totalSize + output.size() + suffix.get(0).length() > expectedSize) {
                    return output.stream().reduce("", (a, b) -> a + " " + b);
                }
                if (Objects.equals(suffix.get(0), ""))
                    return output.stream().reduce("", (a, b) -> a + " " + b);
                output.add(suffix.get(0));
                totalSize += suffix.get(0).length();
            } else {
                rn = r.nextInt(suffix.size());
                if (totalSize + output.size() + suffix.get(rn).length() > expectedSize) {
                    return output.stream().reduce("", (a, b) -> a + " " + b);
                }
                output.add(suffix.get(rn));
                totalSize += suffix.get(rn).length();
            }
            if (totalSize + output.size() >= expectedSize)
                return output.stream().reduce("", (a, b) -> a + " " + b);
            n++;
            prefix = output.stream().skip(n).limit(keySize).reduce("", (a, b) -> a + " " + b).trim();
        }
    }

}