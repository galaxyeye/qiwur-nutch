/*
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
package org.apache.nutch.indexwriter.solr;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.nutch.metadata.SolrConstants;
import org.apache.nutch.util.ObjectCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SolrMappingReader {

  public static Logger LOG = LoggerFactory.getLogger(SolrMappingReader.class);

  /**
   * We do not map a name to another for solr
   * */
  public class MappingField {
    public MappingField(String name, String type, boolean indexed, boolean stored, boolean required, boolean multiValued) {
      this.name = name;
      this.mappedName = name;
      this.type = type;
      this.indexed = indexed;
      this.stored = stored;
      this.required = required;
      this.multiValued = multiValued;
    }
    public String name;
    public String mappedName;
    public String type;
    public boolean indexed;
    public boolean stored;
    public boolean required;
    public boolean multiValued;
  }

  private Configuration conf;

  private Map<String, MappingField> keyMap = new HashMap<>();
  private String uniqueKey = "id";

  public static synchronized SolrMappingReader getInstance(Configuration conf) {
    ObjectCache cache = ObjectCache.get(conf);

    SolrMappingReader instance = (SolrMappingReader) cache.getObject(SolrMappingReader.class.getName());
    if (instance == null) {
      instance = new SolrMappingReader(conf);
      cache.setObject(SolrMappingReader.class.getName(), instance);
    }

    return instance;
  }

  protected SolrMappingReader(Configuration conf) {
    this.conf = conf;
    parseMapping();
  }

  private void parseMapping() {
    String mappingFile = conf.get(SolrConstants.MAPPING_FILE, "solrindex-mapping.xml");
    InputStream ssInputStream = conf.getConfResourceAsInputStream(mappingFile);

    List<String> solrFields = Lists.newArrayList();
    InputSource inputSource = new InputSource(ssInputStream);
    try {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      DocumentBuilder builder = factory.newDocumentBuilder();
      Document document = builder.parse(inputSource);
      Element rootElement = document.getDocumentElement();
      NodeList fieldList = rootElement.getElementsByTagName("field");
      if (fieldList.getLength() > 0) {
        for (int i = 0; i < fieldList.getLength(); i++) {
          Element element = (Element) fieldList.item(i);

          String name = element.getAttribute("name");
          String type = element.getAttribute("type");
          String indexed = element.getAttribute("indexed");
          String stored = element.getAttribute("stored");
          String required = element.getAttribute("required");
          String multiValued = element.getAttribute("multiValued");

          boolean bIndexed = indexed != null && indexed.equalsIgnoreCase("true");
          boolean bStored = stored != null && stored.equalsIgnoreCase("true");
          boolean bRequired = required != null && required.equalsIgnoreCase("true");
          boolean bMultiValued = multiValued != null && multiValued.equalsIgnoreCase("true");

          MappingField mappingFiled = new MappingField(name, type, bIndexed, bStored, bRequired, bMultiValued);

          solrFields.add(name);
          keyMap.put(name, mappingFiled);
        }
      }

      LOG.info("Registered " + solrFields.size() + " solr fields : " + StringUtils.join(solrFields, ", "));

      NodeList uniqueKeyItem = rootElement.getElementsByTagName("uniqueKey");
      if (uniqueKeyItem.getLength() > 1) {
        LOG.warn("More than one unique key definitions found in solr index mapping, using default 'id'");
        uniqueKey = "id";
      } else if (uniqueKeyItem.getLength() == 0) {
        LOG.warn("No unique key definition found in solr index mapping using, default 'id'");
      } else {
        uniqueKey = uniqueKeyItem.item(0).getFirstChild().getNodeValue();
      }
    } catch (SAXException|IOException|ParserConfigurationException e) {
      LOG.warn(e.toString());
    }
  }

  public Map<String, MappingField> getKeyMap() {
    return keyMap;
  }

  public String mapKeyIfExists(String key) throws IOException {
    if (keyMap.containsKey(key)) {
      return key;
    }
    return null;
  }

  public boolean isMultiValued(String key) throws IOException {
    if (keyMap.containsKey(key)) {
      return keyMap.get(key).multiValued;
    }
    return false;
  }
}
