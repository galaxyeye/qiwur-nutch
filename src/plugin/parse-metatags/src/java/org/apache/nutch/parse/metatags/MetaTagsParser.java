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
package org.apache.nutch.parse.metatags;

import org.apache.avro.util.Utf8;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.metadata.Metadata;
import org.apache.nutch.parse.HTMLMetaTags;
import org.apache.nutch.parse.Parse;
import org.apache.nutch.parse.ParseFilter;
import org.apache.nutch.storage.WebPage;
import org.apache.nutch.storage.WebPage.Field;
import org.apache.nutch.storage.WrappedWebPage;
import org.w3c.dom.DocumentFragment;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.Map.Entry;

/**
 * Parse HTML meta tags (keywords, description) and store them in the parse
 * metadata so that they can be indexed with the index-metadata plugin with the
 * prefix 'metatag.'. Metatags are matched ignoring case.
 */
public class MetaTagsParser implements ParseFilter {

  private static final Log LOG = LogFactory.getLog(MetaTagsParser.class.getName());

  private Configuration conf;

  public static final String PARSE_META_PREFIX = "meta_";

  private Set<String> metatagset = new HashSet<>();

  public MetaTagsParser() {
    System.out.println("****************************MetaTagsParser***********************");
  }

  public void setConf(Configuration conf) {
    this.conf = conf;
    // specify whether we want a specific subset of metadata
    // by default take everything we can find
    String[] values = conf.getStrings("metatags.names", "*");
    for (String val : values) {
      metatagset.add(val.toLowerCase(Locale.ROOT));
    }
  }

  public Configuration getConf() {
    return this.conf;
  }

  public Parse filter(String url, WebPage page, Parse parse, HTMLMetaTags metaTags, DocumentFragment doc) {
    // temporary map: cannot concurrently iterate over and modify page metadata
    Map<CharSequence, ByteBuffer> metadata = new HashMap<>();

    // check in the metadata first : the tika-parser
    // might have stored the values there already.
    // Values are then additionally stored with the prefixed key.
    page.getMetadata().keySet().stream().map(CharSequence::toString).forEach(
        key -> addIndexedMetatags(metadata, key, WrappedWebPage.wrap(page).getMetadata(key))
    );

    // add temporary metadata to page metadata
    for (Entry<CharSequence, ByteBuffer> entry : metadata.entrySet()) {
      page.getMetadata().put(entry.getKey(), entry.getValue());
    }

    Metadata generalMetaTags = metaTags.getGeneralTags();
    for (String tagName : generalMetaTags.names()) {
      // multiple values of a metadata field are separated by '\t' in storage.
      StringBuilder sb = new StringBuilder();
      for (String value : generalMetaTags.getValues(tagName)) {
        if (sb.length() > 0) {
          sb.append("\t");
        }
        sb.append(value);
      }
      addIndexedMetatags(page.getMetadata(), tagName, sb.toString());
    }

    Properties httpequiv = metaTags.getHttpEquivTags();
    Enumeration<?> tagNames = httpequiv.propertyNames();
    while (tagNames.hasMoreElements()) {
      String name = (String) tagNames.nextElement();
      String value = httpequiv.getProperty(name);
      addIndexedMetatags(page.getMetadata(), name, value);
    }

    return parse;
  }

  /**
   * Check whether the metatag is in the list of metatags to be indexed (or if
   * '*' is specified). If yes, add it to parse metadata.
   */
  private void addIndexedMetatags(Map<CharSequence, ByteBuffer> metadata, String metatag, String value) {
    // System.out.println(metatag + " -> " + value);

    String lcMetatag = metatag.toLowerCase(Locale.ROOT);
    if (metatagset.contains("*") || metatagset.contains(lcMetatag)) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("Found meta tag: " + lcMetatag + "\t" + value);
      }
      metadata.put(new Utf8(PARSE_META_PREFIX + lcMetatag), ByteBuffer.wrap(value.getBytes()));
    }
  }

  @Override
  public Collection<Field> getFields() {
    return null;
  }

}
