package org.apache.nutch.jobs.index;

/**
 * Created by vincent on 16-10-21.
 * Copyright @ 2013-2016 Warpspeed Information. All rights reserved
 */

import com.google.common.collect.Lists;
import org.apache.commons.lang.ArrayUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.mapreduce.lib.output.NullOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.SystemDefaultHttpClient;
import org.apache.nutch.metadata.Nutch;
import org.apache.nutch.util.ConfigUtils;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class for deleting duplicate documents from a solr index.
 *
 * The algorithm goes like follows:
 *
 * Preparation:
 * <ol>
 * <li>Query the solr server for the number of documents (say, N)</li>
 * <li>Partition N among M map tasks. For example, if we have two map tasks the
 * first map task will deal with solr documents from 0 - (N / 2 - 1) and the
 * second will deal with documents from (N / 2) to (N - 1).</li>
 * </ol>
 *
 * MapReduce:
 * <ul>
 * <li>Map: Identity map where keys are digests and values are
 * {@link SolrRecord} instances(which contain id, boost and timestamp)</li>
 * <li>Reduce: After map, {@link SolrRecord}s with the same digest will be
 * grouped together. Now, of these documents with the same digests, delete all
 * of them except the one with the highest score (boost field). If two (or more)
 * documents have the same score, then the document with the latest timestamp is
 * kept. Again, every other is deleted from solr index.</li>
 * </ul>
 *
 * Note that we assume that two documents in a solr index will never have the
 * same URL. So this class only deals with documents with <b>different</b> URLs
 * but the same digest.
 */
public class SolrDeleteDuplicates
    extends
    Reducer<Text, SolrDeleteDuplicates.SolrRecord, Text, SolrDeleteDuplicates.SolrRecord>
    implements Tool {

  @SuppressWarnings("deprecation")
  private static HttpClient HTTP_CLIENT = new SystemDefaultHttpClient();
  
  /**
   * @return SolrClient
   */
  public static ArrayList<SolrClient> getSolrClients(String[] solrUrls, String[] zkHosts, String collection) {
    ArrayList<SolrClient> solrClients = new ArrayList<>();

    for (String solrUrl : solrUrls) {
      SolrClient client = new HttpSolrClient.Builder(solrUrl)
          .withHttpClient(HTTP_CLIENT)
          .build();
      solrClients.add(client);
    }

    if (solrClients.isEmpty()) {
      CloudSolrClient client = getCloudSolrClient(zkHosts);
      client.setDefaultCollection(collection);
      solrClients.add(client);
    }

    return solrClients;
  }

  public static SolrClient getSolrClient(String[] solrUrls, String[] zkHosts, String collection) {
    ArrayList<SolrClient> solrClients = getSolrClients(solrUrls, zkHosts, collection);
    if (solrClients.isEmpty()) {
      return null;
    }
    return solrClients.get(0);
  }

  public static CloudSolrClient getCloudSolrClient(String... zkHosts) {
    CloudSolrClient client = new CloudSolrClient.Builder()
        .withZkHost(Lists.newArrayList(zkHosts))
        .withHttpClient(HTTP_CLIENT)
        .build();

    client.setParallelUpdates(true);
    client.connect();

    return client;
  }
  
  public static final String ID_FIELD = "id";

  public static final String BOOST_FIELD = "boost";

  public static final String TIMESTAMP_FIELD = "tstamp";

  public static final String DIGEST_FIELD = "digest";
  
  public static final Logger LOG = LoggerFactory.getLogger(SolrDeleteDuplicates.class);

  private static final String SOLR_GET_ALL_QUERY = ID_FIELD + ":[* TO *]";

  private static final int NUM_MAX_DELETE_REQUEST = 1000;

  private Configuration conf;

  private String[] solrUrls = ArrayUtils.EMPTY_STRING_ARRAY;
  private String[] zkHosts = ArrayUtils.EMPTY_STRING_ARRAY;
  private String collection;

  private SolrClient solrClient;

  private int numDeletes = 0;

  private UpdateRequest updateRequest = new UpdateRequest();

  public static class SolrRecord implements Writable {

    private float boost;
    private long tstamp;
    private String id;

    public SolrRecord() {
    }

    public SolrRecord(String id, float boost, long tstamp) {
      this.id = id;
      this.boost = boost;
      this.tstamp = tstamp;
    }

    public String getId() {
      return id;
    }

    public float getBoost() {
      return boost;
    }

    public long getTstamp() {
      return tstamp;
    }

    public void readSolrDocument(SolrDocument doc) {
      id = (String) doc.getFieldValue(ID_FIELD);
      boost = (Float) doc.getFieldValue(BOOST_FIELD);

      Date buffer = (Date) doc.getFieldValue(TIMESTAMP_FIELD);
      tstamp = buffer.getTime();
    }

    @Override
    public void readFields(DataInput in) throws IOException {
      id = Text.readString(in);
      boost = in.readFloat();
      tstamp = in.readLong();
    }

    @Override
    public void write(DataOutput out) throws IOException {
      Text.writeString(out, id);
      out.writeFloat(boost);
      out.writeLong(tstamp);
    }
  }

  public static class SolrInputSplit extends InputSplit implements Writable {

    private int docBegin;
    private int numDocs;

    public SolrInputSplit() {
    }

    public SolrInputSplit(int docBegin, int numDocs) {
      this.docBegin = docBegin;
      this.numDocs = numDocs;
    }

    public int getDocBegin() {
      return docBegin;
    }

    @Override
    public long getLength() throws IOException {
      return numDocs;
    }

    @Override
    public String[] getLocations() throws IOException {
      return new String[] {};
    }

    @Override
    public void readFields(DataInput in) throws IOException {
      docBegin = in.readInt();
      numDocs = in.readInt();
    }

    @Override
    public void write(DataOutput out) throws IOException {
      out.writeInt(docBegin);
      out.writeInt(numDocs);
    }
  }

  public static class SolrRecordReader extends RecordReader<Text, SolrRecord> {

    private int currentDoc = 0;
    private int numDocs;
    private Text text;
    private SolrRecord record;
    private SolrDocumentList solrDocs;

    public SolrRecordReader(SolrDocumentList solrDocs, int numDocs) {
      this.solrDocs = solrDocs;
      this.numDocs = numDocs;
    }

    @Override
    public void initialize(InputSplit split, TaskAttemptContext context)
        throws IOException, InterruptedException {
      text = new Text();
      record = new SolrRecord();
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public float getProgress() throws IOException {
      return currentDoc / (float) numDocs;
    }

    @Override
    public Text getCurrentKey() throws IOException, InterruptedException {
      return text;
    }

    @Override
    public SolrRecord getCurrentValue() throws IOException,
        InterruptedException {
      return record;
    }

    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {
      if (currentDoc >= numDocs) {
        return false;
      }

      SolrDocument doc = solrDocs.get(currentDoc);
      String digest = (String) doc.getFieldValue(DIGEST_FIELD);
      text.set(digest);
      record.readSolrDocument(doc);

      currentDoc++;
      return true;
    }
  }

  public class SolrInputFormat extends InputFormat<Text, SolrRecord> {

    @Override
    public List<InputSplit> getSplits(JobContext context) throws IOException,
        InterruptedException {
      Configuration conf = context.getConfiguration();
      int numSplits = context.getNumReduceTasks();
      solrClient  = getSolrClient(solrUrls, zkHosts, collection);

      final SolrQuery solrQuery = new SolrQuery(SOLR_GET_ALL_QUERY);
      solrQuery.setFields(ID_FIELD);
      solrQuery.setRows(1);

      QueryResponse response;
      try {
        response = solrClient.query(solrQuery);
      } catch (final SolrServerException e) {
        throw new IOException(e);
      }

      int numResults = (int) response.getResults().getNumFound();
      int numDocsPerSplit = (numResults / numSplits);
      int currentDoc = 0;
      List<InputSplit> splits = new ArrayList<>();
      for (int i = 0; i < numSplits - 1; i++) {
        splits.add(new SolrInputSplit(currentDoc, numDocsPerSplit));
        currentDoc += numDocsPerSplit;
      }
      splits.add(new SolrInputSplit(currentDoc, numResults - currentDoc));

      return splits;
    }

    @Override
    public RecordReader<Text, SolrRecord> createRecordReader(InputSplit split,
                                                             TaskAttemptContext context) throws IOException, InterruptedException {
      Configuration conf = context.getConfiguration();
      SolrInputSplit solrSplit = (SolrInputSplit) split;
      final int numDocs = (int) solrSplit.getLength();

      SolrQuery solrQuery = new SolrQuery(SOLR_GET_ALL_QUERY);
      solrQuery.setFields(ID_FIELD, BOOST_FIELD, TIMESTAMP_FIELD, DIGEST_FIELD);
      solrQuery.setStart(solrSplit.getDocBegin());
      solrQuery.setRows(numDocs);

      QueryResponse response;
      try {
        response = solrClient.query(solrQuery);
      } catch (final SolrServerException e) {
        throw new IOException(e);
      }

      final SolrDocumentList solrDocs = response.getResults();
      return new SolrRecordReader(solrDocs, numDocs);
    }
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  @Override
  public void setup(Context job) throws IOException {
    conf = job.getConfiguration();

    solrUrls = conf.getStrings(Nutch.PARAM_SOLR_SERVER_URL, ArrayUtils.EMPTY_STRING_ARRAY);
    zkHosts = conf.getStrings(Nutch.PARAM_SOLR_ZK, ArrayUtils.EMPTY_STRING_ARRAY);
    collection = conf.get(Nutch.PARAM_SOLR_COLLECTION);

    // dateFormat = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.ENGLISH);

    if (solrUrls == null && zkHosts == null) {
      String message = "Either SOLR URL or Zookeeper URL is required. " +
          "Use -D " + Nutch.PARAM_SOLR_SERVER_URL + " or -D " + Nutch.PARAM_SOLR_ZK;
      LOG.error(message);
      throw new RuntimeException("Failed to init SolrIndexWriter");
    }

    solrClient  = getSolrClient(solrUrls, zkHosts, collection);
  }

  @Override
  public void cleanup(Context context) throws IOException {
    try {
      if (numDeletes > 0) {
        updateRequest.process(solrClient);

        solrClient.commit();
      }
    } catch (SolrServerException e) {
      throw new IOException(e);
    }
  }

  @Override
  public void reduce(Text key, Iterable<SolrRecord> values, Context context) throws IOException {
    Iterator<SolrRecord> iterator = values.iterator();
    SolrRecord recordToKeep = iterator.next();
    while (iterator.hasNext()) {
      SolrRecord solrRecord = iterator.next();
      if (solrRecord.getBoost() > recordToKeep.getBoost()
          || (solrRecord.getBoost() == recordToKeep.getBoost()
          && solrRecord.getTstamp() > recordToKeep.getTstamp())) {
        updateRequest.deleteById(recordToKeep.id);
        recordToKeep = solrRecord;
      } else {
        updateRequest.deleteById(solrRecord.id);
      }

      numDeletes++;
      if (numDeletes >= NUM_MAX_DELETE_REQUEST) {
        try {
          updateRequest.process(solrClient);
        } catch (SolrServerException e) {
          throw new IOException(e);
        }
        updateRequest = new UpdateRequest();
        numDeletes = 0;
      }
    }
  }

  public boolean dedup(String solrUrl) throws IOException, InterruptedException, ClassNotFoundException {
    getConf().set(Nutch.ARG_SOLR_URL, solrUrl);

    Job job = Job.getInstance(getConf(), "solrdedup");

    job.setInputFormatClass(SolrInputFormat.class);
    job.setOutputFormatClass(NullOutputFormat.class);
    job.setMapOutputKeyClass(Text.class);
    job.setMapOutputValueClass(SolrRecord.class);
    job.setMapperClass(Mapper.class);
    job.setReducerClass(SolrDeleteDuplicates.class);

    return job.waitForCompletion(true);
  }

  public int run(String[] args) throws IOException, InterruptedException,
      ClassNotFoundException {
    if (args.length != 1) {
      System.err.println("Usage: SolrDeleteDuplicates <solr url>");
      return 1;
    }

    boolean result = dedup(args[0]);
    if (result) {
      LOG.info("SolrDeleteDuplicates: done.");
      return 0;
    }

    return -1;
  }

  public static void main(String[] args) throws Exception {
    int result = ToolRunner.run(ConfigUtils.create(), new SolrDeleteDuplicates(), args);
    System.exit(result);
  }
}
