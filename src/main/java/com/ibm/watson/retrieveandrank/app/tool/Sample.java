/* BEGIN_COPYRIGHT
 *
 * IBM Confidential
 * OCO Source Materials
 *
 * 5727-I17
 * (C) Copyright IBM Corp. 2011, 2015 All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 *
 * END_COPYRIGHT
 */

package com.ibm.watson.retrieveandrank.app.tool;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.util.EntityUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.ModifiableSolrParams;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ibm.watson.search.client.ClusterLifecycleClient;
import com.ibm.watson.search.client.config.SolrConfigManager;
import com.ibm.watson.search.client.http.HttpClusterLifecycleClient;
import com.ibm.watson.search.client.http.HttpSolrConfigManager;
import com.ibm.watson.search.client.http.WatsonSolrClient;
import com.ibm.watson.search.models.SolrClusterResponse;
import com.ibm.watson.search.models.SolrClusterResponse.Status;

/**
 * Sample App
 */
public class Sample {
    private static final String BASE_OUTPUT_PATH = "/WATSON_ROOT/l2r_data/cran data/";

    private static final String RUNTIME_CSV_FILE_PATH_FEATURE = BASE_OUTPUT_PATH + "runtime.csv";
    private static final String RUNTIME_CSV_FILE_PATH_OOB_SOLR = BASE_OUTPUT_PATH + "runtime_oob_solr.csv";
    private static final String RANKER_TRAINING_STATUS = "Training";
    private static final String RANKER_STATUS_JSON_ATTR = "status";
    private static final String RANKER_URL_JSON_ATTR = "url";
    private static final String RANKER_ID_JSON_ATTR = "ranker_id";
    private static final int MAX_QUERY_ATTEMPTS = 3;
    private static final int GT_SPLIT_RANDOM_SEED = 77845;
    private static final int PERCENT_TO_HOLD_BACK_FOR_TEST = 11;
    private static final String PATH_TO_QUERY_FILE = BASE_OUTPUT_PATH + "cran.qry";
    private static final String SPACE_SEPARATOR = " ";
    private static final String BODY_FIELD = "body";
    private static final String BIBLIOGRAPHY_FIELD = "bibliography";
    private static final String AUTHOR_FIELD = "author";
    private static final String TITLE_FIELD = "title";
    private static final String PATH_TO_CONFIG_ZIP = BASE_OUTPUT_PATH + "Archive.zip";
    private static final String FIELD_LIST_PARAM = "fl";
    private static final String CSV_TRAIN_OUTPUT_FILE = BASE_OUTPUT_PATH + "train.csv";
    private static final String CSV_TEST_OUTPUT_FILE = BASE_OUTPUT_PATH + "test.csv";

    private static final String FCSELECT_REQUEST_HANDLER = "/fcselect";
    private static final String ID_FIELD = "id";
    private static final String GROUND_TRUTH_FILE = BASE_OUTPUT_PATH + "cranqrel";
    private static final String FEATURE_VECTOR_FIELD = "featureVector";
    private static final String SCORE_FIELD = "score";
    private static final String GROUND_TRUTH_HEADER = "ground_truth";
    private static final String SCORE_HEADER = "score";
    private static final String QUERY_ID_HEADER = "question_id";
    private static final String ANSWER_ID_HEADER = "answer_id";
    private static final String FEATURE_VECTOR_DELIM = " ";
    private static final Character CSV_DELIM = ',';
    private static final String QUERY_TOKEN = ".W";
    private static final String ID_TOKEN = ".I ";
    private static final String BODY_TOKEN = ".W";
    private static final String BIBLIOGRAPHY_TOKEN = ".B";
    private static final String AUTHOR_TOKEN = ".A";
    private static final String TITLE_TOKEN = ".T";
    private static final String CONFIG_NAME = "CONFIG_NAME";
    private final static String SOLR_CLUSTER_PATH = "/solr_clusters/";
    private static final String COLLECTION_NAME = "COLLECTION_NAME";

    // dev vars
    private final static String USERNAME_DEV = "cbc810ce-c3f9-432e-89bb-38841aab36b8";
    private final static String PASSWORD_DEV = "avWT9irfIB5K";
    private final static String RETRIEVE_AND_RANK_ENDPOINT_DEV = "https://gateway-s.watsonplatform.net/search/api/v1";
    static String SOLR_CLUSTER_ID_DEV = "scadec93b8_2cfe_4d15_8b35_c71bc463a363";
    private static final String RANKER_PASSWORD_DEV = PASSWORD_DEV;
    private static final String RANKER_USERNAME_DEV = USERNAME_DEV;
    private static final String RANKER_BASE_URL_DEV = RETRIEVE_AND_RANK_ENDPOINT_DEV + "/rankers";

    // pstg vars
    private final static String USERNAME_PSTG = "cf5e014c-3c90-4fa3-9f2a-8618b6717374";
    private final static String PASSWORD_PSTG = "Kt7ysHc0OYB4";
    private final static String RETRIEVE_AND_RANK_ENDPOINT_PSTG = "https://gateway-s.watsonplatform.net/search/api/v1";
    static String SOLR_CLUSTER_ID_PSTG = "CREATE ME";
    private static final String RANKER_PASSWORD_PSTG = PASSWORD_PSTG;
    private static final String RANKER_USERNAME_PSTG = USERNAME_PSTG;
    private static final String RANKER_BASE_URL_PSTG = RETRIEVE_AND_RANK_ENDPOINT_PSTG + "/rankers";

    // pprd vars
    private final static String USERNAME_PPRD = "YOUR_USERNAME";
    private final static String PASSWORD_PPRD = "YOUR_PASSWORD";
    private final static String RETRIEVE_AND_RANK_ENDPOINT_PPRD = "https://gateway.watsonplatform.net/search/api/v1";
    static String SOLR_CLUSTER_ID_PPRD = "YOUR_SOLR_CLUSTER_ID";
    private static final String RANKER_PASSWORD_PPRD = PASSWORD_PPRD;
    private static final String RANKER_USERNAME_PPRD = USERNAME_PPRD;
    private static final String RANKER_BASE_URL_PPRD = RETRIEVE_AND_RANK_ENDPOINT_PPRD + "/rankers";

    private final static String ENV = "dev";

    final static Map<Integer, List<Answer>> testQuestionsToAnswers = Maps.newHashMap();
    final static Map<Integer, List<Answer>> trainQuestionsToAnswers = Maps.newHashMap();
    static Map<Integer, Query> allQueries;

    static WatsonSolrClient solrClient;
    static ClusterLifecycleClient lifecycleClient;
    static SolrConfigManager solrConfigManager;
    static String USERNAME;
    static String PASSWORD;
    static String RETRIEVE_AND_RANK_ENDPOINT;
    static String SOLR_CLUSTER_ID;
    static String RANKER_PASSWORD;
    static String RANKER_USERNAME;
    static String RANKER_BASE_URL;
    static boolean createCluster = false;
    static boolean cleanup = false;
    static boolean uploadConfig = false;
    static boolean createCollection = false;
    static boolean uploadDocs = false;
    static boolean queryForGroundTruthAndCreateCSVs = false;
	static boolean createNewRanker = false;
    static boolean runtimeQuery = true;
    static boolean runNormalQueries = true;
    static boolean doStuffWithRanker = false; // don't change this - just playing with stuff
    private static ByteArrayOutputStream csvFeatureContent = new ByteArrayOutputStream();
    private static ByteArrayOutputStream csvSearchResult = new ByteArrayOutputStream();
    public static void main(String[] args) {
        try {
            System.out.println("using env: " + ENV);
            switch (ENV) {
            case "dev":
                USERNAME = "cbc810ce-c3f9-432e-89bb-38841aab36b8";
                PASSWORD = "avWT9irfIB5K";
                RETRIEVE_AND_RANK_ENDPOINT = RETRIEVE_AND_RANK_ENDPOINT_DEV;
                SOLR_CLUSTER_ID = SOLR_CLUSTER_ID_DEV;
                RANKER_PASSWORD = RANKER_PASSWORD_DEV;
                RANKER_USERNAME = RANKER_USERNAME_DEV;
                RANKER_BASE_URL = RANKER_BASE_URL_DEV;
                break;
            case "pprd":
                USERNAME = USERNAME_PPRD;
                PASSWORD = PASSWORD_PPRD;
                RETRIEVE_AND_RANK_ENDPOINT = RETRIEVE_AND_RANK_ENDPOINT_PPRD;
                SOLR_CLUSTER_ID = SOLR_CLUSTER_ID_PPRD;
                RANKER_PASSWORD = RANKER_PASSWORD_PPRD;
                RANKER_USERNAME = RANKER_USERNAME_PPRD;
                RANKER_BASE_URL = RANKER_BASE_URL_PPRD;
                break;
            case "pstg":
                USERNAME = USERNAME_PSTG;
                PASSWORD = PASSWORD_PSTG;
                RETRIEVE_AND_RANK_ENDPOINT = RETRIEVE_AND_RANK_ENDPOINT_PSTG;
                SOLR_CLUSTER_ID = SOLR_CLUSTER_ID_PSTG;
                RANKER_PASSWORD = RANKER_PASSWORD_PSTG;
                RANKER_USERNAME = RANKER_USERNAME_PSTG;
                RANKER_BASE_URL = RANKER_BASE_URL_PSTG;
                break;
            default:
                throw new RuntimeException("invalid env: " + ENV);
            }
            final URI lifeCycleUri = new URI(RETRIEVE_AND_RANK_ENDPOINT);
            final URI solrUri = new URI(RETRIEVE_AND_RANK_ENDPOINT + SOLR_CLUSTER_PATH + SOLR_CLUSTER_ID);
            final URI rankerUri = new URI(RANKER_BASE_URL);
            final HttpClientBuilder builder = createHttpClientBuilder(rankerUri);

            lifecycleClient = new HttpClusterLifecycleClient(lifeCycleUri, USERNAME, PASSWORD);
            solrClient = new WatsonSolrClient(solrUri, USERNAME, PASSWORD);

            solrConfigManager =
                    new HttpSolrConfigManager(solrUri, USERNAME, PASSWORD);

            // read in all queries
            allQueries = readInAllQueries();
            StringBuffer json = new StringBuffer("{\"queries\":[");
            StringBuffer queries = null;
            for(Query q : allQueries.values()){
                if(queries != null){
                    queries.append(",");
                }
                else{
                    queries = new StringBuffer();
                }
                queries.append("{\"query\":\"" + q.query.trim() + "\", \"id\" :\"" + q.actualId + "\"}");
            }
            json.append(queries.toString());
            json.append("]}");
            System.out.println(json);
            // read in GT
            final Map<Integer, List<Answer>> groundTruthAnswerMap = readInGroundTruth();

            // need to split up the GT into train & test
            splitGroundTruth(groundTruthAnswerMap, testQuestionsToAnswers, trainQuestionsToAnswers,
                    PERCENT_TO_HOLD_BACK_FOR_TEST);
            System.out.println("test set size " + testQuestionsToAnswers.size() + " "
                    + testQuestionsToAnswers.keySet().toString());
            System.out.println("train set size " + trainQuestionsToAnswers.size());

            final List<Record> records = readData();

            // 1 create a cluster - only needed once... this takes a while to do so don't run it every time!
            if (createCluster) {
                createCluster();
            }

            if (cleanup) {
                cleanup();
            }

            // 2 upload configuration
            if (uploadConfig) {
                uploadConfiguration();
            }

            // 3 create a collection
            if (createCollection) {
                createCollection();
            }

            // 4 upload documents
            if (uploadDocs) {
                uploadDocuments(records);
            }

            // 5 query for ground truth and produce the CSVs
            if (queryForGroundTruthAndCreateCSVs) {
                queryForGroundTruthAndCreateCSVs();
            }

            // setting these is useful if you don't want to create the ranker each time and instead point to
            // an existing ranker

            final String rankerId = "2A6BA3-rank-44";
            final String rankerUrl = "https://gateway-d.watsonplatform.net/search/api/v1/rankers/" + rankerId;

            // 6 create a ranker and train it
            String specificRankerUrl = rankerUrl; // this will get overridden by any ranker you create here
            if (createNewRanker) {
                specificRankerUrl = createRanker(rankerUri, builder);
            }
            // 7 simulate runtime workflow
            if (runtimeQuery) {
                runtimeQueryAndBuildAnswerCSV(builder, specificRankerUrl);
            }

            // this is for test purpose only - IGNORE THIS
            if (doStuffWithRanker) {
                doStuffWithRanker(builder);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * IGNORE THIS ENTIRE FUNCTION
     */
    private static void doStuffWithRanker(HttpClientBuilder builder)
            throws URISyntaxException, ClientProtocolException, IOException {

        JsonNode node = null;
        // default existing ranker
        final String rankerId = "1D7EA2-rank-1";
        final String rankerUrl = "https://gateway-d.watsonplatform.net/search/api/v1/rankers/" + rankerId;

        double solrAccuracy = 0.0;
        double hectorAccuracy = 0.0;
        double l2rAccuracy = 0.0;
        double solrRecall10 = 0.0;
        double hectorRecall10 = 0.0;
        double l2rRecall10 = 0.0;
        double solrRecall5 = 0.0;
        double hectorRecall5 = 0.0;
        double l2rRecall5 = 0.0;
        double solrRecall3 = 0.0;
        double hectorRecall3 = 0.0;
        double l2rRecall3 = 0.0;
        int count = 1;
        for (final int queryId : testQuestionsToAnswers.keySet()) {
            boolean solrRecall10Found = false;
            boolean hectorRecall10Found = false;
            boolean l2rRecall10Found = false;
            boolean solrRecall3Found = false;
            boolean hectorRecall3Found = false;
            boolean l2rRecall3Found = false;
            boolean solrRecall5Found = false;
            boolean hectorRecall5Found = false;
            boolean l2rRecall5Found = false;
            // execute the runtime ranking against the pre-existing csv file for the test query
            try (CloseableHttpClient httpClient = builder.build();) {
                final URI specificRankerRuntime = new URI(rankerUrl + "/rank");
                final HttpPost runtimeRank = new HttpPost(specificRankerRuntime);
                final MultipartEntityBuilder meb = MultipartEntityBuilder.create();
                meb.addBinaryBody("answer_data",
                        new File(CSV_TEST_OUTPUT_FILE.replaceFirst("\\.csv", queryId + ".csv")));
                runtimeRank.setEntity(meb.build());

                try (CloseableHttpResponse response = httpClient.execute(runtimeRank)) {
                    final String entityResponse = EntityUtils.toString(response.getEntity());
                    // System.out.println(entityResponse);
                    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                        throw new RuntimeException(
                                "something went wrong talking to ranker: " + response.getStatusLine().getStatusCode());
                    }
                    final ObjectMapper om = new ObjectMapper();
                    node = om.readValue(entityResponse, JsonNode.class);
                }
            }

            // need tow file readers... 1 for the normal solr response that was previously captured
            // and another for the test csv output (i.e. hector-only)
            final List<RankerAnswer> answerList = Lists.newArrayList();
            try (CSVReader testFeatureReader = new CSVReader(
                    new FileReader(new File(CSV_TEST_OUTPUT_FILE.replaceFirst("\\.csv", queryId + ".csv"))), CSV_DELIM,
                    CSVWriter.NO_QUOTE_CHARACTER); CSVReader normalReader = new CSVReader(
                            new FileReader(
                                    new File(CSV_TEST_OUTPUT_FILE.replaceFirst("\\.csv", queryId + "_normal.csv"))),
                            CSV_DELIM,
                            CSVWriter.NO_QUOTE_CHARACTER)) {
                // skip headers
                testFeatureReader.readNext();
                normalReader.readNext();
                final JsonNode answers = node.get("answers");
                final Map<String, Double> answerToScoreMap = Maps.newHashMap();
                final Query queryToRun = allQueries.get(queryId);
                System.out.println("using query id: " + queryId + " (alt id: " + queryToRun.providedId + ")");
                System.out.println("query string: " + queryToRun.query);

                System.out.format("%48s%48s%48s", "Normal", "Hector", "L2R");
                System.out.println();

                final Map<Integer, Integer> answerToGT = Maps.newHashMap();
                for (final Answer a : testQuestionsToAnswers.get(queryId)) {
                    int rel = a.rel;
                    if (rel <= 0) {
                        rel = 0;
                    } else {
                        rel = 4 - a.rel + 1;
                    }

                    if (rel < 0) {
                        rel = 0;
                    }

                    answerToGT.put(a.id, rel);
                }
                for (int i = 0; i < answers.size(); i++) {
                    final String[] solrCSVValues = normalReader.readNext();
                    final String[] hectorCSVValues = testFeatureReader.readNext();
                    final JsonNode answer = answers.get(i);
                    final RankerAnswer rankerAnswer = new RankerAnswer();
                    rankerAnswer.answerId = answer.get(ANSWER_ID_HEADER).asText();
                    rankerAnswer.score = answer.get("score").asDouble();
                    answerToScoreMap.put(rankerAnswer.answerId, rankerAnswer.score);
                    Integer l2rGT = answerToGT.get(Integer.parseInt(rankerAnswer.answerId));
                    if (l2rGT == null) {
                        l2rGT = 0;
                    }
                    Integer solrGt = answerToGT.get(Integer.parseInt(solrCSVValues[1]));
                    if (solrGt == null) {
                        solrGt = 0;
                    }
                    Integer hectorGT = answerToGT.get(Integer.parseInt(hectorCSVValues[0]));
                    if (hectorGT == null) {
                        hectorGT = 0;
                    }
                    if (i == 0) {
                        solrAccuracy = solrGt > 0 ? (solrAccuracy * count + 1) / (count + 1) : solrAccuracy;
                        hectorAccuracy = hectorGT > 0 ? (hectorAccuracy * count + 1) / (count + 1) : hectorAccuracy;
                        l2rAccuracy = l2rGT > 0 ? (l2rAccuracy * count + 1) / (count + 1) : l2rAccuracy;
                    }
                    if (solrGt > 0) {
                        if (i < 3 && !solrRecall3Found) {
                            solrRecall3 = (solrRecall3 * count + 1) / (count + 1);
                            solrRecall3Found = true;
                        }
                        if (i < 5 && !solrRecall5Found) {
                            solrRecall5 = (solrRecall5 * count + 1) / (count + 1);
                            solrRecall5Found = true;
                        }
                        if (!solrRecall10Found) {
                            solrRecall10 = (solrRecall10 * count + 1) / (count + 1);
                            solrRecall10Found = true;
                        }
                    }
                    if (hectorGT > 0) {
                        if (i < 3 && !hectorRecall3Found) {
                            hectorRecall3 = (hectorRecall3 * count + 1) / (count + 1);
                            hectorRecall3Found = true;
                        }
                        if (i < 5 && !hectorRecall5Found) {
                            hectorRecall5 = (hectorRecall5 * count + 1) / (count + 1);
                            hectorRecall5Found = true;
                        }
                        if (!hectorRecall10Found) {
                            hectorRecall10 = (hectorRecall10 * count + 1) / (count + 1);
                            hectorRecall10Found = true;
                        }
                    }
                    if (rankerAnswer.score > 0) {
                        if (i < 3 && !l2rRecall3Found) {
                            l2rRecall3 = (l2rRecall3 * count + 1) / (count + 1);
                            l2rRecall3Found = true;
                        }
                        if (i < 5 && !l2rRecall5Found) {
                            l2rRecall5 = (l2rRecall5 * count + 1) / (count + 1);
                            l2rRecall5Found = true;
                        }
                        if (!l2rRecall10Found) {
                            l2rRecall10 = (l2rRecall10 * count + 1) / (count + 1);
                            l2rRecall10Found = true;
                        }
                    }
                    System.out.format("%48s%48s%48s", solrCSVValues[1] + " (" + solrCSVValues[2] + ", " + solrGt + ")",
                            hectorCSVValues[0] + " ("
                                    + hectorCSVValues[1] + ", " + hectorGT + ")",
                            rankerAnswer.answerId + " (" + rankerAnswer.score + ", "
                                    + l2rGT + ")");
                    System.out.println();
                    answerList.add(rankerAnswer);
                }
            }
            count++;
        }

        System.out.format("%48s%48s%48s%48s", "", "Normal", "Hector", "L2R");
        System.out.println();
        System.out.format("%48s%48s%48s%48s", "Accuracy", solrAccuracy, hectorAccuracy, l2rAccuracy);
        System.out.println();
        System.out.format("%48s%48s%48s%48s", "recall@10", solrRecall10, hectorRecall10, l2rRecall10);
        System.out.println();
        System.out.format("%48s%48s%48s%48s", "recall@5", solrRecall5, hectorRecall5, l2rRecall5);
        System.out.println();
        System.out.format("%48s%48s%48s%48s", "recall@3", solrRecall3, hectorRecall3, l2rRecall3);

    }

    private static String createRanker(URI rankerUri, HttpClientBuilder builder)
            throws IOException, JsonParseException, JsonMappingException, ClientProtocolException, URISyntaxException,
            InterruptedException {
        String specificRankerUrl;
        String specificRankerId;
        try (CloseableHttpClient httpClient = builder.build();) {
            final HttpPost createRanker = new HttpPost(rankerUri);
            final MultipartEntityBuilder meb = MultipartEntityBuilder.create();
            // using our training csv
            meb.addBinaryBody("training_data", new File(CSV_TRAIN_OUTPUT_FILE));
            // assign our ranker a unique name
            meb.addTextBody("training_metadata", "{\"name\":\"Test Ranker Cranfield\"}");

            createRanker.setEntity(meb.build());
            System.out.println("Uploading training data to ranker....");
            try (CloseableHttpResponse response = httpClient.execute(createRanker)) {
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new RuntimeException(
                            "something went wrong talking to ranker: " + response.getStatusLine().getStatusCode());
                }

                final String entityResponse = EntityUtils.toString(response.getEntity());
                System.out.println("Ranker created!! " + entityResponse);
                final ObjectMapper om = new ObjectMapper();
                final JsonNode node = om.readValue(entityResponse, JsonNode.class);
                specificRankerId = node.get(RANKER_ID_JSON_ATTR).asText();
                specificRankerUrl = node.get(RANKER_URL_JSON_ATTR).asText();
                System.out.println(specificRankerId);
            }
        }
        // now we need to poll on the ranker status until it's done training.
        try (CloseableHttpClient httpClient = builder.build();) {
            // use the rankerUrl we were returned from above.
            final URI specificRankerStatus = new URI(specificRankerUrl);
            final HttpGet rankerStatus = new HttpGet(specificRankerStatus);
            System.out.println("Polling for status of ranker...");
            while (true) {
                try (CloseableHttpResponse response = httpClient.execute(rankerStatus)) {
                    final String entityResponse = EntityUtils.toString(response.getEntity());
                    final ObjectMapper om = new ObjectMapper();
                    final JsonNode node = om.readValue(entityResponse, JsonNode.class);
                    final String status = node.get(RANKER_STATUS_JSON_ATTR).asText();
                    System.out.println(entityResponse);
                    if (!RANKER_TRAINING_STATUS.equals(status)) {
                        if (!"Available".equals(status)) {
                            throw new RuntimeException(
                                    "Problem with training ranker(status=" + status + "): " + entityResponse);
                        }
                        System.out.println("Ranker is Available!");
                        break;
                    }
                    Thread.sleep(1000);
                }
            }
        }
        return specificRankerUrl;
    }

    private static HttpClientBuilder createHttpClientBuilder(final URI rankerUri) {
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(rankerUri.getHost(), rankerUri.getPort()),
                new UsernamePasswordCredentials(RANKER_USERNAME, RANKER_PASSWORD));

        final HttpClientBuilder builder = HttpClientBuilder.create()
                .setMaxConnTotal(128)
                .setMaxConnPerRoute(32)
                .setDefaultRequestConfig(RequestConfig.copy(RequestConfig.DEFAULT)
                        .setRedirectsEnabled(true).build());
        builder.setDefaultCredentialsProvider(credentialsProvider);
        builder.addInterceptorFirst(new PreemptiveAuthInterceptor());
        return builder;
    }

    private static void solrRuntimeQuery(String query, boolean featureVector, OutputStream os)
            throws IOException, SolrServerException, InterruptedException {
        boolean headersPrinted = false;
        
        Writer w = new OutputStreamWriter(os);
        try (final CSVWriter csvWriter = new CSVWriter(w,
                CSV_DELIM, CSVWriter.NO_QUOTE_CHARACTER)) {

            final SolrQuery featureSolrQuery = new SolrQuery(query);
            // specify the fcselect request handler for the feature query
            if (featureVector) {
                featureSolrQuery.setRequestHandler(FCSELECT_REQUEST_HANDLER);
            }

            // bring back the id, score, and featureVector for the feature query
            featureSolrQuery.setParam(FIELD_LIST_PARAM, ID_FIELD, SCORE_FIELD, FEATURE_VECTOR_FIELD);
            // need to ask for enough rows to ensure the correct answer is included in the resultset
            featureSolrQuery.setRows(1000);
            final QueryRequest featureRequest = new QueryRequest(featureSolrQuery);

            System.out.println("runtime query: " + query);
            // this leverages the plugin
            final QueryResponse featureResponse = processSolrRequest(featureRequest);

            System.out.println(" --> " + featureResponse.getResults().size());

            final Iterator<SolrDocument> it = featureResponse.getResults().iterator();
            while (it.hasNext()) {
                final SolrDocument doc = it.next();
                final List<String> csvRowValues = Lists.newArrayList();

                final Integer answerId = Integer.parseInt((String) doc.getFieldValue(ID_FIELD));
                // if we have GT it's a question-to-feature csv
                csvRowValues.add(String.valueOf(answerId));
                csvRowValues.add(String.valueOf(doc.getFieldValue(SCORE_FIELD)));
                StringTokenizer features;
                int numFeatures = 0;
                if (featureVector) {
                    features =
                            new StringTokenizer(((String) doc.getFieldValue(FEATURE_VECTOR_FIELD)).trim(),
                                    FEATURE_VECTOR_DELIM);
                    numFeatures = features.countTokens();
                    while (features.hasMoreTokens()) {
                        final String feature = features.nextToken();
                        csvRowValues.add(feature);
                    }
                }
                // add all the features returned in the vector
                if (!headersPrinted) {
                    headersPrinted = true;
                    final List<String> headerList = Lists.newArrayList();
                    headerList.add(ANSWER_ID_HEADER);
                    headerList.add(SCORE_HEADER);
                    for (int i = 1; i <= numFeatures; i++) {
                        headerList.add("f" + i);
                    }
                    csvWriter.writeNext(headerList.toArray(new String[0]));
                }
                // need to add in the headers if this is our first time through
                csvWriter.writeNext(csvRowValues.toArray(new String[0]));
            }
        }
    }

    private static void runtimeQueryAndBuildAnswerCSV(HttpClientBuilder builder, String specificRankerUrl)
            throws IOException, URISyntaxException, SolrServerException, InterruptedException {

        // grab a random query from the test set
        final List<Integer> shuffledQueries = Lists.newArrayList(testQuestionsToAnswers.keySet());
        Collections.shuffle(shuffledQueries);
        final Query queryToRun = allQueries.get(shuffledQueries.get(0));
        final int queryId = queryToRun.actualId;
        System.out.println("using query id: " + queryId + " (" + queryToRun.providedId + ")");

        final List<Answer> gtForQuery = testQuestionsToAnswers.get(queryId);
        final Map<Integer, Answer> answersForQueryById = Maps.newHashMap();

        for (final Answer a : gtForQuery) {
            answersForQueryById.put(a.id, a);
        }
        // run the feature query
        String q = "what are the structural and aeroelastic problems associated with flight of high speed aircraft";
        solrRuntimeQuery(q, true, csvFeatureContent);
        // run against regular solr - just to compare
        solrRuntimeQuery(q, false, csvSearchResult);

        JsonNode node = null;
        // upload the runtime query w/ feature vectors to the ranker
        try (CloseableHttpClient httpClient = builder.build();) {
            final URI specificRankerRuntime = new URI(specificRankerUrl + "/rank");
            final HttpPost runtimeRank = new HttpPost(specificRankerRuntime);
            final MultipartEntityBuilder meb = MultipartEntityBuilder.create();
            byte[] arr = csvFeatureContent.toByteArray();
            System.out.println(new String(arr));
            meb.addBinaryBody("answer_data", IOUtils.toInputStream(new String(arr)));
            /*
             * meb.addBinaryBody("answer_data",
                    new File(RUNTIME_CSV_FILE_PATH_FEATURE));
             */
            runtimeRank.setEntity(meb.build());
            System.out.println("Uploading runtime csv to ranker...");
            try (CloseableHttpResponse response = httpClient.execute(runtimeRank)) {
                final String entityResponse = EntityUtils.toString(response.getEntity());
                System.out.println(entityResponse);
                if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                    throw new RuntimeException(
                            "something went wrong talking to ranker: " + response.getStatusLine().getStatusCode());
                }
                System.out.println("Ranker returned: " + entityResponse);
                final ObjectMapper om = new ObjectMapper();
                node = om.readValue(entityResponse, JsonNode.class);
            }
        }

        final JsonNode answers = node.get("answers");
        final List<RankerAnswer> answerList = Lists.newArrayList();

        for (int i = 0; i < answers.size(); i++) {
            final JsonNode answer = answers.get(i);
            final RankerAnswer rankerAnswer = new RankerAnswer();
            rankerAnswer.answerId = answer.get(ANSWER_ID_HEADER).asText();
            rankerAnswer.score = answer.get("score").asDouble();
            answerList.add(rankerAnswer);
        }

        System.out.println("ranker answers.size == " + answerList.size());
        // now you go back to the search engine to get the results values - or you go to wherever you
        // need to get data to display

        // just retrieve what you need to display - not all of the results
        final List<String> answersToRetrieve = Lists.newArrayList();
        System.out.format("%48s%48s", "OOB Solr", "L2R");
        System.out.println();
        try (CSVReader csvReader = new CSVReader(
                new FileReader(new File(RUNTIME_CSV_FILE_PATH_OOB_SOLR)), CSV_DELIM,
                CSVWriter.NO_QUOTE_CHARACTER)) {

            // skip the headers
            csvReader.readNext();

            for (int i = 0; i < 100 && i < answerList.size(); i++) {
                final RankerAnswer rankerAnswer = answerList.get(i);
                final String[] csvValues = csvReader.readNext();
                answersToRetrieve.add(rankerAnswer.answerId);
                final Answer gtLookupForRanker = answersForQueryById.get(Integer.parseInt(rankerAnswer.answerId));
                final Answer gtLookupForSolr = answersForQueryById.get(Integer.parseInt(csvValues[0]));
                int relForRankerAnswer = 0;
                int relForSolrAnswer = 0;
                if (gtLookupForRanker != null) {
                    relForRankerAnswer = gtLookupForRanker.rel;
                }
                if (gtLookupForSolr != null) {
                    relForSolrAnswer = gtLookupForSolr.rel;
                }
                System.out.format("%48s%48s", csvValues[0] + " (" + csvValues[1] + ","
                        + relForSolrAnswer + ")",
                        rankerAnswer.answerId + " (" + rankerAnswer.score + ","
                                + relForRankerAnswer + ")");
                System.out.println();
            }
        }

        final ModifiableSolrParams params = new ModifiableSolrParams();
        System.out.println("Retrieving data from solr for top re-ranked results");
        final SolrDocumentList docs = solrClient.getById(COLLECTION_NAME, answersToRetrieve, params);
        final Iterator<SolrDocument> it = docs.iterator();
        while (it.hasNext()) {
            final SolrDocument doc = it.next();
            System.out.println("id: " + doc.getFirstValue(ID_FIELD));
            System.out.println("\ttitle: " + doc.getFirstValue(TITLE_FIELD));
            System.out.println("\tbody: " + doc.getFirstValue(BODY_FIELD));
        }
    }

    @SuppressWarnings("unchecked")
    private static void cleanup() throws SolrServerException, IOException {
        boolean doDeleteCollection = false;
        boolean doDeleteConfig = false;
        final CollectionAdminRequest.List listCollectionRequest = new CollectionAdminRequest.List();
        final CollectionAdminResponse listResponse = listCollectionRequest.process(solrClient);
        final List<String> collections = (List<String>) listResponse.getResponse().get("collections");

        for (final String collection : collections) {
            if (COLLECTION_NAME.equals(collection)) {
                doDeleteCollection = true;
            }
        }

        if (doDeleteCollection) {
            final CollectionAdminRequest.Delete deleteCollectionRequest = new CollectionAdminRequest.Delete();
            deleteCollectionRequest.setCollectionName(COLLECTION_NAME);

            System.out.println("Deleting collection...");
            final CollectionAdminResponse response = deleteCollectionRequest.process(solrClient);
            if (!response.isSuccess()) {
                throw new IllegalStateException(
                        "Failed to delete collection: " + response.getErrorMessages().toString());
            }
            System.out.println("Collection deleted.");
        }

        for (final String config : solrConfigManager.listConfigurations()) {
            if (CONFIG_NAME.equals(config)) {
                doDeleteConfig = true;
            }
        }

        if (doDeleteConfig) {
            System.out.println("Deleting config...");
            solrConfigManager.deleteConfiguration(CONFIG_NAME);
            System.out.println("Config deleted...");
        }
    }

    private static void queryForGroundTruthAndCreateCSVs()
            throws IOException, SolrServerException, InterruptedException {
        runTrainingQueriesAndProduceCSV(allQueries, trainQuestionsToAnswers);
        runTestQueriesAndProduceCSV(allQueries, testQuestionsToAnswers);
    }

    private static void runTestQueriesAndProduceCSV(Map<Integer, Query> queries,
            Map<Integer, List<Answer>> test) throws IOException, SolrServerException, InterruptedException {
        final boolean includeGT = false;
        queryAndCreateCSV(queries, test, includeGT, CSV_TEST_OUTPUT_FILE);
    }

    private static void runTrainingQueriesAndProduceCSV(final Map<Integer, Query> queries,
            final Map<Integer, List<Answer>> train)
                    throws IOException, SolrServerException, InterruptedException {
        final boolean includeGT = true;
        queryAndCreateCSV(queries, train, includeGT, CSV_TRAIN_OUTPUT_FILE);
    }

    private static void queryAndCreateCSV(Map<Integer, Query> queries,
            Map<Integer, List<Answer>> queryIdToAnswers, final boolean includeGT, final String filepath)
                    throws IOException, SolrServerException, InterruptedException {
        boolean appendResultsToFile = false;
        boolean headersPrinted = false;
        int queryCount = 0;
        String actualFilePath = filepath;
        // run queries and produce training CSV
        for (final Integer queryId : queryIdToAnswers.keySet()) {
            if (!includeGT) {
                // if we're not including GT produce a file per query
                actualFilePath = filepath.replaceFirst("\\.csv", queryId + ".csv");
                headersPrinted = false;
            } else if (queryCount > 0) {
                // otherwise we're producing training data so we need 1 big file - append to
                // what was created on the first query
                appendResultsToFile = true;
            }
            queryCount++;
            try (final CSVWriter featureCSVWriter = new CSVWriter(
                    new FileWriter(actualFilePath, appendResultsToFile),
                    CSV_DELIM, CSVWriter.NO_QUOTE_CHARACTER)) {
                final Query q = queries.get(queryId);
                final Map<Integer, Integer> answerIdToRel = buildAnswerToRelMap(queryIdToAnswers, q);

                final SolrQuery featureSolrQuery = new SolrQuery(q.query);
                final SolrQuery normalSolrQuery = new SolrQuery(q.query);
                // specify the fcselect request handler for the feature query
                featureSolrQuery.setRequestHandler(FCSELECT_REQUEST_HANDLER);

                // bring back the id, score, and featureVector for the feature query
                featureSolrQuery.setParam(FIELD_LIST_PARAM, ID_FIELD, SCORE_FIELD, FEATURE_VECTOR_FIELD);
                // for normal query just bring back id and score
                normalSolrQuery.setParam(FIELD_LIST_PARAM, ID_FIELD, SCORE_FIELD);
                // need to ask for enough rows to ensure the correct answer is included in the resultset
                featureSolrQuery.setRows(1000);
                normalSolrQuery.setRows(1000);
                // need to do a query request ourselves since we want to use the custom /fcselect request handler
                // instead of
                // /select
                final QueryRequest featureRequest = new QueryRequest(featureSolrQuery);
                final QueryRequest normalRequest = new QueryRequest(normalSolrQuery);
                System.out.println("q.providedId = " + q.providedId + ", q.actualId = " + q.actualId);
                System.out.println("q.query = " + q.query);

                // this really only applies easily to cranfield since the queries are simple. We run
                // this against raw solr (no hector plugin) to get the ranking that solr provides. We
                // mash the title and body fields into a common field that we query against.
                if (runNormalQueries) {
                    try (final CSVWriter normalCSVWriter = new CSVWriter(
                            new FileWriter(actualFilePath.replaceFirst("\\.csv", "_normal.csv"), true),
                            CSV_DELIM, CSVWriter.NO_QUOTE_CHARACTER)) {

                        final QueryResponse normalResponse = processSolrRequest(normalRequest);
                        final Iterator<SolrDocument> it = normalResponse.getResults().iterator();
                        if (!headersPrinted) {
                            final List<String> headerList = Lists.newArrayList();
                            headerList.add(QUERY_ID_HEADER);
                            headerList.add(ANSWER_ID_HEADER);
                            headerList.add(SCORE_HEADER);
                            normalCSVWriter.writeNext(headerList.toArray(new String[0]));
                        }
                        while (it.hasNext()) {
                            final SolrDocument doc = it.next();
                            final List<String> csvRowValues = Lists.newArrayList();

                            final Integer answerId = Integer.parseInt((String) doc.getFieldValue(ID_FIELD));
                            csvRowValues.add(String.valueOf(q.actualId));
                            csvRowValues.add(String.valueOf(answerId));
                            csvRowValues.add(String.valueOf(doc.getFieldValue(SCORE_FIELD)));

                            // need to add in the headers if this is our first time
                            normalCSVWriter.writeNext(csvRowValues.toArray(new String[0]));
                        }
                    }
                }

                // this leverages the plugin
                final QueryResponse featureResponse = processSolrRequest(featureRequest);

                System.out.println(" --> " + featureResponse.getResults().size());

                final Iterator<SolrDocument> it = featureResponse.getResults().iterator();
                while (it.hasNext()) {
                    final SolrDocument doc = it.next();
                    final List<String> csvRowValues = Lists.newArrayList();

                    final Integer answerId = Integer.parseInt((String) doc.getFieldValue(ID_FIELD));
                    // if we have GT it's a question-to-feature csv
                    if (includeGT) {
                        csvRowValues.add(String.valueOf(q.actualId));
                    } else {
                        // otherwise it's a result set - answer-to-feature csv
                        csvRowValues.add(String.valueOf(answerId));
                    }
                    csvRowValues.add(String.valueOf(doc.getFieldValue(SCORE_FIELD)));
                    final StringTokenizer features =
                            new StringTokenizer(((String) doc.getFieldValue(FEATURE_VECTOR_FIELD)).trim(),
                                    FEATURE_VECTOR_DELIM);
                    final int numFeatures = features.countTokens();

                    // add all the features returned in the vector
                    while (features.hasMoreTokens()) {
                        final String feature = features.nextToken();
                        csvRowValues.add(feature);
                    }
                    if (includeGT) {
                        // look up the GT relevance
                        Integer rel = answerIdToRel.get(answerId);
                        if (rel == null || rel < 0) {
                            rel = 0;
                        }
                        // add in the ground truth value
                        // NOTE: the data we have has rel values from -1 to 5. 1 is the "best" and 5 means "not
                        // relevant"
                        // NOTE: not sure what -1 means, but it's in there too... so ranges for us are -1.0 to 1.0
                        int finalRel = 0;
                        if (rel != 0) {
                            finalRel = 4 - rel + 1; // make 4 the highest value
                            if (finalRel < 0) {
                                finalRel = 0;
                            }
                        }
                        csvRowValues.add(String.valueOf(finalRel));
                    }
                    // need to add in the headers if this is our first time through
                    if (!headersPrinted) {
                        headersPrinted = true;
                        final List<String> headerList = Lists.newArrayList();
                        if (includeGT) {
                            headerList.add(QUERY_ID_HEADER);
                        } else {
                            headerList.add(ANSWER_ID_HEADER);
                        }
                        headerList.add(SCORE_HEADER);
                        for (int i = 1; i <= numFeatures; i++) {
                            headerList.add("f" + i);
                        }
                        if (includeGT) {
                            headerList.add(GROUND_TRUTH_HEADER);
                        }
                        featureCSVWriter.writeNext(headerList.toArray(new String[0]));
                    }
                    featureCSVWriter.writeNext(csvRowValues.toArray(new String[0]));
                }
            }
        }
    }

    private static QueryResponse processSolrRequest(final QueryRequest request)
            throws IOException, SolrServerException, InterruptedException {
        int currentAttempt = 0;
        QueryResponse response;
        while (true) {
            try {
                currentAttempt++;
                response = request.process(solrClient, COLLECTION_NAME);
                break;
            } catch (final Exception e) {
                System.out.println("Attempt #" + currentAttempt + " failed:");
                if (currentAttempt < MAX_QUERY_ATTEMPTS) {
                    e.printStackTrace();
                    System.out.println("retrying (" + currentAttempt + ").....");
                    Thread.sleep(1000);
                } else {
                    throw e;
                }
            }
        }
        return response;
    }

    private static void splitGroundTruth(Map<Integer, List<Answer>> answerMap, Map<Integer, List<Answer>> test,
            Map<Integer, List<Answer>> train, int percentToHoldBackForTest) {

        final Random rand = new Random(GT_SPLIT_RANDOM_SEED);

        for (final Integer i : answerMap.keySet()) {
            final List<Answer> answers = answerMap.get(i);
            final int r = rand.nextInt(100) + 1;
            if (r < percentToHoldBackForTest) {
                test.put(i, answers);
            } else {
                train.put(i, answers);
            }
        }
    }

    private static Map<Integer, Integer> buildAnswerToRelMap(final Map<Integer, List<Answer>> answerMap,
            final Query q) {
        // for each query - we build the answer-to-rel map
        final Map<Integer, Integer> answerIdToRel = Maps.newHashMap();
        final List<Answer> answers = answerMap.get(q.actualId);
        // final List<String> answerIDs = Lists.newArrayList();
        if (answers != null) {
            for (final Answer a : answers) {
                // answerIDs.add(String.valueOf(a.id) + ":" + String.valueOf(a.rel));
                answerIdToRel.put(a.id, a.rel);
            }
        }
        return answerIdToRel;
    }

    private static Map<Integer, List<Answer>> readInGroundTruth() throws FileNotFoundException, IOException {
        final File data = new File(GROUND_TRUTH_FILE);
        final Map<Integer, List<Answer>> answerMap = Maps.newHashMap();
        String line;
        StringBuffer json = new StringBuffer("{\"gt\":[");
        boolean first = true;
        try (BufferedReader br = new BufferedReader(new FileReader(data))) {
            while ((line = br.readLine()) != null) {
                final StringTokenizer st = new StringTokenizer(line, SPACE_SEPARATOR);
                final Answer answer = new Answer();
                answer.queryId = Integer.parseInt(st.nextToken());
                answer.id = Integer.parseInt(st.nextToken());
                answer.rel = Integer.parseInt(st.nextToken());
                if(!first){
                    json.append(",");
                }
                first = false;
                json.append("{\"qid");
                json.append("\":");
                json.append("\"");
                json.append(answer.queryId);
                json.append("\",");
                json.append("\"aid");
                json.append("\":");
                json.append("\"");
                json.append(answer.id);
                json.append("\",");
                json.append("\"rel");
                json.append("\":");
                json.append("\"");
                json.append(answer.rel);
                json.append("\"}");
                
                List<Answer> existing = answerMap.get(answer.queryId);
                if (existing == null) {
                    existing = Lists.newArrayList();
                    answerMap.put(answer.queryId, existing);
                }
                existing.add(answer);
            }
        }
        json.append("]}");
        System.out.println(json.toString());
        return answerMap;
    }

    private static Map<Integer, Query> readInAllQueries() throws FileNotFoundException, IOException {
        final File data = new File(PATH_TO_QUERY_FILE);
        final Map<Integer, Query> queries = Maps.newHashMap();
        try (BufferedReader br = new BufferedReader(new FileReader(data))) {
            String line;
            Query currentQuery = null;
            String id;
            String current = "";
            int count = 1;
            while ((line = br.readLine()) != null) {
                if (line.startsWith(ID_TOKEN)) {
                    // get rid of leading zeroes
                    id = line.substring(3, line.length()).replaceFirst("^0+", "");
                    if (currentQuery != null) {
                        queries.put(currentQuery.actualId, currentQuery);
                    }
                    currentQuery = new Query();
                    currentQuery.providedId = Integer.parseInt(id);
                    currentQuery.actualId = count++;
                } else if (line.equals(QUERY_TOKEN)) {
                    current = line;
                } else {
                    switch (current) {
                    case QUERY_TOKEN:
                        if (currentQuery != null) {
                            currentQuery.query += FEATURE_VECTOR_DELIM + line;
                        }
                        break;
                    default:
                        throw new RuntimeException("no clue: " + current);
                    }
                }
            }
            if (currentQuery != null) {
                queries.put(currentQuery.actualId, currentQuery);
            }
        }
        return queries;
    }

    private static void uploadDocuments(List<Record> records) throws IOException, SolrServerException {

        final List<SolrInputDocument> batch = Lists.newArrayList();
        int total = 0;
        for (final Record r : records) {
            final SolrInputDocument doc = new SolrInputDocument();
            doc.addField(ID_FIELD, r.id);
            doc.addField(TITLE_FIELD, r.title);
            doc.addField(AUTHOR_FIELD, r.author);
            doc.addField(BIBLIOGRAPHY_FIELD, r.bibliography);
            doc.addField(BODY_FIELD, r.body);
            batch.add(doc);
            total++;
            // batch things to make it more efficient to index
            if (batch.size() >= 100) {
                System.out.println("sending batch! total sent so far: " + total);
                solrClient.add(COLLECTION_NAME, batch);
                batch.clear();
            }
        }
        // add any remaining docs in the partial batch
        if (batch.size() > 0) {
            solrClient.add(COLLECTION_NAME, batch);
        }
        System.out.println("committing...");
        solrClient.commit(COLLECTION_NAME);
        System.out.println("commit done...");

    }

    private static List<Record> readData() throws IOException, FileNotFoundException {
        // read in all data
        final File data = new File(BASE_OUTPUT_PATH + "cran.all.1400");
        final List<Record> records = Lists.newArrayList();
        try (final BufferedReader br = new BufferedReader(new FileReader(data))) {
            String line;
            String current = "";
            String id = "";
            Record currentRecord = null;

            while ((line = br.readLine()) != null) {
                if (line.equals(TITLE_TOKEN)) {
                    current = TITLE_TOKEN;
                } else if (line.equals(AUTHOR_TOKEN)) {
                    current = AUTHOR_TOKEN;
                } else if (line.equals(BIBLIOGRAPHY_TOKEN)) {
                    current = BIBLIOGRAPHY_TOKEN;
                } else if (line.equals(BODY_TOKEN)) {
                    current = BODY_TOKEN;
                } else if (line.startsWith(ID_TOKEN)) {
                    id = line.substring(3, line.length());
                    if (currentRecord != null) {
                        records.add(currentRecord);
                    }
                    currentRecord = new Record();
                    currentRecord.id = Integer.parseInt(id);
                } else {
                    if (currentRecord == null) {
                        throw new RuntimeException("found unexpected line token: " + line);
                    }
                    switch (current) {
                    case TITLE_TOKEN:
                        currentRecord.title += SPACE_SEPARATOR + line;
                        break;
                    case AUTHOR_TOKEN:
                        currentRecord.author += SPACE_SEPARATOR + line;
                        break;
                    case BIBLIOGRAPHY_TOKEN:
                        currentRecord.bibliography += SPACE_SEPARATOR + line;
                        break;
                    case BODY_TOKEN:
                        currentRecord.body += SPACE_SEPARATOR + line;
                        break;
                    default:
                        throw new RuntimeException("no clue: " + current);
                    }
                }
            }
            records.add(currentRecord);
        }
        return records;
    }

    private static void createCollection() throws SolrServerException, IOException {
        final CollectionAdminRequest.Create createCollectionRequest = new CollectionAdminRequest.Create();
        createCollectionRequest.setCollectionName(COLLECTION_NAME);
        createCollectionRequest.setConfigName(CONFIG_NAME);

        System.out.println("Creating collection...");
        final CollectionAdminResponse response = createCollectionRequest.process(solrClient);
        if (!response.isSuccess()) {
            throw new IllegalStateException("Failed to create collection: " + response.getErrorMessages().toString());
        }
        System.out.println("Collection created.");
    }

    private static void uploadConfiguration() {
        System.out.println("uploading configuration...");
        solrConfigManager.uploadConfigurationZip(CONFIG_NAME,
                new File(PATH_TO_CONFIG_ZIP));
        System.out.println("configuration uploaded...");
    }

    private static void createCluster() throws InterruptedException {
        SolrClusterResponse clusterStatus = lifecycleClient.createSolrCluster();

        SOLR_CLUSTER_ID = clusterStatus.getSolrClusterId();
        // poll until status is ready
        while ((clusterStatus = lifecycleClient.pollSolrCluster(SOLR_CLUSTER_ID))
                .getSolrClusterStatus() != Status.READY) {
            System.out.println("Cluster not ready yet.  This takes a while...");
            Thread.sleep(5000);
        }
        System.out.println("Cluster is READY!!!");
    }

    private static class Record {
        public String title = "";
        public String author = "";
        public String bibliography = "";
        public String body = "";
        public int id;

        @Override
        public String toString() {
            return "id: " + id + "\ntitle: " + title + "\nauthor: " + author + "\nbibliography: " + bibliography
                    + "\nbody: " + body;
        }
    }

    private static class Answer {
        public int queryId;
        public int id;
        public int rel;
    }

    private static class Query {
        public int providedId;
        public String query = "";
        public int actualId;
    }

    private static class RankerAnswer {
        public String answerId;
        public double score;
    }

    private static class PreemptiveAuthInterceptor implements HttpRequestInterceptor {

        @Override
        public void process(final HttpRequest request, final HttpContext context) throws HttpException {
            final AuthState authState = (AuthState) context.getAttribute(HttpClientContext.TARGET_AUTH_STATE);

            // If no auth scheme available yet, try to initialize it preemptively
            if (authState.getAuthScheme() == null) {
                final CredentialsProvider credsProvider = (CredentialsProvider) context
                        .getAttribute(HttpClientContext.CREDS_PROVIDER);
                final HttpHost targetHost = (HttpHost) context.getAttribute(HttpCoreContext.HTTP_TARGET_HOST);
                final Credentials creds = credsProvider.getCredentials(new AuthScope(targetHost.getHostName(),
                        targetHost.getPort()));
                if (creds == null) {
                    throw new HttpException("No credentials for preemptive authentication");
                }
                authState.update(new BasicScheme(), creds);
            }
        }
    }
}
