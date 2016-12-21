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
package org.apache.nutch.scoring.opic;

import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.persist.graph.Edge;
import org.apache.nutch.persist.WebPage;
import org.apache.nutch.util.ConfigUtils;
import org.apache.nutch.util.TableUtil;
import org.junit.Before;
import org.junit.Test;

import java.text.DecimalFormat;
import java.util.*;
import java.util.Map.Entry;

import static org.junit.Assert.assertTrue;

/**
 * JUnit test for <code>OPICScoringFilter</code>. For an example set of URLs, we
 * simulate inlinks and outlinks of the available graph. By manual calculation,
 * we determined the correct score points of URLs for each depth. For
 * convenience, a Map (dbWebPages) is used to store the calculated scores
 * instead of a persistent data store. At the end of the test, calculated scores
 * in the map are compared to our correct scores and a boolean result is
 * returned.
 * 
 */
public class TestOPICScoringFilter {

  // These lists will be used when simulating the graph
  private Map<String, String[]> linkList = new LinkedHashMap<String, String[]>();
  private final List<Edge> outlinkedScoreData = new ArrayList<Edge>();
  private static final int DEPTH = 3;

  DecimalFormat df = new DecimalFormat("#.###");

  private final String[] seedList = new String[] { "http://a.com", "http://b.com", "http://c.com", };

  // An example graph; shows websites as connected nodes
  private void fillLinks() {
    linkList.put("http://a.com", new String[] { "http://b.com" });
    linkList.put("http://b.com", new String[] { "http://a.com", "http://c.com" });
    linkList.put("http://c.com", new String[] { "http://a.com", "http://b.com", "http://d.com" });
    linkList.put("http://d.com", new String[] {});
  }

  // Previously calculated values for each three depths. We will compare these
  // to the results this test generates
  static HashMap<Integer, HashMap<String, Float>> acceptedScores = new HashMap<Integer, HashMap<String, Float>>() {
    /**
     *
	 */
    private static final long serialVersionUID = 278328450774664407L;

    {
      put(1, new HashMap<String, Float>() {
        /**
		 * 
		 */
        private static final long serialVersionUID = 6145080304388858096L;

        {
          put("http://a.com", new Float(1.833));
          put("http://b.com", 2.333f);
          put("http://c.com", 1.5f);
          put("http://d.com", 0.333f);
        }
      });
      put(2, new HashMap<String, Float>() {
        /**
		 * 
		 */
        private static final long serialVersionUID = 8948751511219885073L;

        {
          put(new String("http://a.com"), new Float(2.666));
          put(new String("http://b.com"), new Float(3.333));
          put(new String("http://c.com"), new Float(2.166));
          put(new String("http://d.com"), new Float(0.278));
        }
      });
      put(3, new HashMap<String, Float>() {
        /**
		 * 
		 */
        private static final long serialVersionUID = -7025018421800845103L;

        {
          put("http://a.com", 3.388f);
          put("http://b.com", 4.388f);
          put("http://c.com", 2.666f);
          put("http://d.com", 0.5f);
        }
      });
    }
  };

  private HashMap<Integer, HashMap<String, Float>> resultScores = new HashMap<>();

  private OPICScoringFilter scoringFilter;

  @Before
  public void setUp() throws Exception {

    Configuration conf = ConfigUtils.create();
    // LinkedHashMap dbWebPages is used instead of a persistent
    // data store for this test class
    Map<String, Map<WebPage, List<Edge>>> dbWebPages = new LinkedHashMap<>();

    // All WebPages stored in this map with an initial true value.
    // After processing, it is set to false.
    Map<String, Boolean> dbWebPagesControl = new LinkedHashMap<String, Boolean>();

    TestOPICScoringFilter self = new TestOPICScoringFilter();
    self.fillLinks();

    float scoreInjected = conf.getFloat("db.score.injected", 1.0f);

    scoringFilter = new OPICScoringFilter();
    scoringFilter.setConf(conf);

    // injecting seed list, with scored attached to webpages
    for (String url : self.seedList) {
      WebPage row = WebPage.newWebPage();
      row.setScore(scoreInjected);
      scoringFilter.injectedScore(url, row);

      List<Edge> scList = new LinkedList<>();
      Map<WebPage, List<Edge>> webPageMap = new HashMap<>();
      webPageMap.put(row, scList);
      dbWebPages.put(TableUtil.reverseUrl(url), webPageMap);
      dbWebPagesControl.put(TableUtil.reverseUrl(url), true);
    }

    // Depth Loop
    for (int i = 1; i <= DEPTH; i++) {
      Iterator<Map.Entry<String, Map<WebPage, List<Edge>>>> iter = dbWebPages.entrySet().iterator();

      // OPIC Score calculated for each website one by one
      while (iter.hasNext()) {
        Map.Entry<String, Map<WebPage, List<Edge>>> entry = iter.next();
        Map<WebPage, List<Edge>> webPageMap = entry.getValue();

        WebPage row = null;
        List<Edge> scoreList = null;
        Iterator<Map.Entry<WebPage, List<Edge>>> iters = webPageMap.entrySet().iterator();
        if (iters.hasNext()) {
          Map.Entry<WebPage, List<Edge>> values = iters.next();
          row = values.getKey();
          scoreList = values.getValue();
        }

        String reverseUrl = entry.getKey();
        String url = TableUtil.unreverseUrl(reverseUrl);
        float score = row.getScore();

        if (dbWebPagesControl.get(TableUtil.reverseUrl(url))) {
          row.setScore(scoringFilter.generatorSortValue(url, row, score));
          dbWebPagesControl.put(TableUtil.reverseUrl(url), false);
        }

        // getting outlinks from testdata
        String[] seedOutlinks = self.linkList.get(url);
        for (String seedOutlink : seedOutlinks) {
          row.getOutlinks().put(seedOutlink, "");
        }

        self.outlinkedScoreData.clear();

        // Existing outlinks are added to outlinkedScoreData
        Map<CharSequence, CharSequence> outlinks = row.getOutlinks();
        for (Entry<CharSequence, CharSequence> e : outlinks.entrySet()) {
          self.outlinkedScoreData.add(new Edge(0.0f, e.getKey().toString(), e.getValue().toString(), Integer.MAX_VALUE));
        }

        scoringFilter.distributeScoreToOutlinks(url, row, self.outlinkedScoreData, outlinks.size());

        // DbUpdate Reducer simulation
        for (Edge sc : self.outlinkedScoreData) {
          if (dbWebPages.get(TableUtil.reverseUrl(sc.getUrl())) == null) {
            // Check each outlink and creates new webpages if it's not
            // exist in database (dbWebPages)
            WebPage outlinkRow = WebPage.newWebPage();

            scoringFilter.initialScore(sc.getUrl(), outlinkRow);
            List<Edge> newScoreList = new LinkedList<>();
            newScoreList.add(sc);
            Map<WebPage, List<Edge>> values = new HashMap<>();
            values.put(outlinkRow, newScoreList);
            dbWebPages.put(TableUtil.reverseUrl(sc.getUrl()), values);
            dbWebPagesControl.put(TableUtil.reverseUrl(sc.getUrl()), true);
          } else {
            // Outlinks are added to list for each webpage
            Map<WebPage, List<Edge>> values = dbWebPages.get(TableUtil.reverseUrl(sc.getUrl()));
            Iterator<Map.Entry<WebPage, List<Edge>>> value = values.entrySet().iterator();
            if (value.hasNext()) {
              Map.Entry<WebPage, List<Edge>> list = value.next();
              scoreList = list.getValue();
              scoreList.add(sc);
            }
          }
        }
      }

      // Simulate Reducing
      for (Map.Entry<String, Map<WebPage, List<Edge>>> page : dbWebPages.entrySet()) {

        String reversedUrl = page.getKey();
        String url = TableUtil.unreverseUrl(reversedUrl);

        Iterator<Map.Entry<WebPage, List<Edge>>> rr = page.getValue().entrySet().iterator();

        List<Edge> inlinkedScoreDataList = null;
        WebPage row = null;
        if (rr.hasNext()) {
          Map.Entry<WebPage, List<Edge>> aa = rr.next();
          inlinkedScoreDataList = aa.getValue();
          row = aa.getKey();
        }
        // Scores are updated here
        scoringFilter.updateScore(url, row, inlinkedScoreDataList);
        inlinkedScoreDataList.clear();
        HashMap<String, Float> result = new HashMap<String, Float>();
        result.put(url, row.getScore());

        resultScores.put(i, result);
      }

    }
  }

  /**
   * Assertion that the accepted and and actual resultant scores are the same.
   */
  @Test
  public void testModeAccept() {
    for (int i = 1; i <= DEPTH; i++) {
      for (String resultUrl : resultScores.get(i).keySet()) {
        String accepted = df.format(acceptedScores.get(i).get(resultUrl));
        System.out.println("Accepted Score: " + accepted);
        String result = df.format(resultScores.get(i).get(resultUrl));
        System.out.println("Resulted Score: " + result);
        assertTrue(accepted.equals(result));
      }
    }
  }
}
