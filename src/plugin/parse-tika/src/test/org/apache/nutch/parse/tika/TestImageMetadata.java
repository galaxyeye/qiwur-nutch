/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.nutch.parse.tika;

import org.apache.nutch.parse.ParseException;
import org.apache.nutch.persist.WebPage;
import org.apache.nutch.protocol.ProtocolException;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

@Ignore("Image parser is not required currently")
public class TestImageMetadata extends TikaTestBase {

  // Make sure sample files are copied to "test.data" as specified in
  private String[] sampleFiles = { "nutch_logo_tm.gif" };

  @Test
  public void testIt() throws ProtocolException, ParseException, IOException {
    for (String sampleFile : sampleFiles) {
      WebPage page = parse(sampleFile);

      assertEquals("121", page.getMetadata("width"));
      assertEquals("48", page.getMetadata("height"));
    }
  }
}
