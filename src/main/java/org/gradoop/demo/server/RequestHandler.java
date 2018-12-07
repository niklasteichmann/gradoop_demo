/*
 * Copyright © 2014 - 2018 Leipzig University (Database Research Group)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradoop.demo.server;

import org.apache.commons.lang.ArrayUtils;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.LocalCollectionOutputFormat;
import org.apache.flink.api.java.tuple.Tuple3;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.gradoop.common.model.impl.pojo.Edge;
import org.gradoop.common.model.impl.pojo.GraphHead;
import org.gradoop.common.model.impl.pojo.Vertex;
import org.gradoop.demo.server.functions.AcceptNoneFilter;
import org.gradoop.demo.server.functions.LabelFilter;
import org.gradoop.demo.server.functions.LabelGroupReducer;
import org.gradoop.demo.server.functions.LabelMapper;
import org.gradoop.demo.server.functions.LabelReducer;
import org.gradoop.demo.server.functions.PropertyKeyMapper;
import org.gradoop.demo.server.pojo.GroupingRequest;
import org.gradoop.demo.server.pojo.SamplingRequest;
import org.gradoop.flink.io.impl.csv.CSVDataSource;
import org.gradoop.flink.model.impl.epgm.GraphCollection;
import org.gradoop.flink.model.impl.epgm.LogicalGraph;
import org.gradoop.flink.model.impl.operators.aggregation.functions.count.EdgeCount;
import org.gradoop.flink.model.impl.operators.aggregation.functions.count.VertexCount;
import org.gradoop.flink.model.impl.operators.aggregation.functions.max.MaxEdgeProperty;
import org.gradoop.flink.model.impl.operators.aggregation.functions.max.MaxVertexProperty;
import org.gradoop.flink.model.impl.operators.aggregation.functions.min.MinEdgeProperty;
import org.gradoop.flink.model.impl.operators.aggregation.functions.min.MinVertexProperty;
import org.gradoop.flink.model.impl.operators.aggregation.functions.sum.SumEdgeProperty;
import org.gradoop.flink.model.impl.operators.aggregation.functions.sum.SumVertexProperty;
import org.gradoop.flink.model.impl.operators.grouping.Grouping;
import org.gradoop.flink.model.impl.operators.grouping.GroupingStrategy;
import org.gradoop.flink.model.impl.operators.matching.common.MatchStrategy;
import org.gradoop.flink.model.impl.operators.matching.common.statistics.GraphStatistics;
import org.gradoop.flink.model.impl.operators.sampling.PageRankSampling;
import org.gradoop.flink.model.impl.operators.sampling.RandomEdgeSampling;
import org.gradoop.flink.model.impl.operators.sampling.RandomLimitedDegreeVertexSampling;
import org.gradoop.flink.model.impl.operators.sampling.RandomNonUniformVertexSampling;
import org.gradoop.flink.model.impl.operators.sampling.RandomVertexEdgeSampling;
import org.gradoop.flink.model.impl.operators.sampling.RandomVertexNeighborhoodSampling;
import org.gradoop.flink.model.impl.operators.sampling.RandomVertexSampling;
import org.gradoop.flink.model.impl.operators.sampling.functions.Neighborhood;
import org.gradoop.flink.model.impl.operators.sampling.functions.VertexDegree;
import org.gradoop.flink.util.GradoopFlinkConfig;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Handles REST requests to the server.
 */
@Path("")
public class RequestHandler {

  private final String META_FILENAME = "/metadata.json";

  private static final ExecutionEnvironment ENV = ExecutionEnvironment.createLocalEnvironment();
  private GradoopFlinkConfig config = GradoopFlinkConfig.createConfig(ENV);

  /**
   * Takes a database name via a POST request and returns the keys of all
   * vertex and edge properties, and a boolean value specifying if the property has a numerical
   * type. The return is a string in the JSON format, for easy usage in a JavaScript web page.
   *
   * @param databaseName name of the loaded database
   * @return A JSON containing the vertices and edges property keys
   */
  @POST
  @Path("/keys/{databaseName}")
  @Produces("application/json;charset=utf-8")
  public Response getKeysAndLabels(@PathParam("databaseName") String databaseName) {
    URL meta = RequestHandler.class.getResource("/data/" + databaseName + META_FILENAME);
    try {
      if (meta == null) {
        JSONObject result = computeKeysAndLabels(databaseName);
        if (result == null) {
          return Response.serverError().build();
        }
        return Response.ok(result.toString()).build();
      } else {
        JSONObject result = readKeysAndLabels(databaseName);
        if (result == null) {
          return Response.serverError().build();
        }
        return Response.ok(readKeysAndLabels(databaseName).toString()).build();
      }
    } catch (Exception e) {
      e.printStackTrace();
      // if any exception is thrown, return an error to the client
      return Response.serverError().build();
    }
  }

  @POST
  @Path("/cypher")
  @Produces("application/x-www-form-urlencoded;charset=utf-8")
  public Response executeCypher(
    @FormParam("databaseName") String databaseName,
    @FormParam("query") String query,
    @DefaultValue("false") @FormParam("attacheData") boolean attacheData) {
    //load the database
    String path = getDatabaseURL(databaseName).getPath();

    CSVDataSource source = new CSVDataSource(path, config);

    LogicalGraph graph = source.getLogicalGraph();

    // TODO load proper statistics
    GraphStatistics graphStatistics = new GraphStatistics(1, 1, 1, 1);
    GraphCollection res = graph.cypher(query, attacheData, MatchStrategy.HOMOMORPHISM, MatchStrategy.ISOMORPHISM, graphStatistics);

    return createResponse(res);
  }

  /**
   * Compute property keys and labels.
   *
   * @param databaseName name of the database
   * @return JSONObject containing property keys and labels
   */
  private JSONObject computeKeysAndLabels(String databaseName) throws IOException {

    String path = getDatabaseURL(databaseName).getPath();

    CSVDataSource source = new CSVDataSource(path, config);

    LogicalGraph graph = source.getLogicalGraph();

    JSONObject jsonObject = new JSONObject();

    //compute the vertex and edge property keys and return them
    try {
      jsonObject.put("vertexKeys", getVertexKeys(graph));
      jsonObject.put("edgeKeys", getEdgeKeys(graph));
      jsonObject.put("vertexLabels", getVertexLabels(graph));
      jsonObject.put("edgeLabels", getEdgeLabels(graph));
      String dataPath = getDatabaseURL(databaseName).getFile();
      FileWriter writer = new FileWriter(dataPath + META_FILENAME);
      jsonObject.write(writer);
      writer.flush();
      writer.close();

      return jsonObject;
    } catch (Exception e) {
      e.printStackTrace();
      // if any exception is thrown, return an error to the client
      return null;
    }
  }

  /**
   * Read the property keys and labels from the buffered JSON.
   *
   * @param databaseName name of the database
   * @return JSONObject containing the property keys and labels
   * @throws IOException   if reading fails
   * @throws JSONException if JSON creation fails
   */
  private JSONObject readKeysAndLabels(String databaseName) throws IOException, JSONException {
    String dataPath = getDatabaseURL(databaseName).getFile();
    String content =
      new String(Files.readAllBytes(Paths.get(dataPath + META_FILENAME)), StandardCharsets.UTF_8);

    return new JSONObject(content);
  }

  /**
   * Takes any given graph and creates a JSONArray containing the vertex property keys and a
   * boolean,
   * specifying it the property has a numerical type.
   *
   * @param graph input graph
   * @return JSON array with property keys and boolean, that is true if the property type is
   * numercial
   * @throws Exception if the collecting of the distributed data fails
   */
  private JSONArray getVertexKeys(LogicalGraph graph) throws Exception {

    List<Tuple3<Set<String>, String, Boolean>> vertexKeys = graph.getVertices()
      .flatMap(new PropertyKeyMapper<>())
      .groupBy(1)
      .reduceGroup(new LabelGroupReducer())
      .collect();

    return buildArrayFromKeys(vertexKeys);
  }

  /**
   * Takes any given graph and creates a JSONArray containing the edge property keys and a boolean,
   * specifying it the property has a numerical type.
   *
   * @param graph input graph
   * @return JSON array with property keys and boolean, that is true if the property type is
   * numercial
   * @throws Exception if the collecting of the distributed data fails
   */
  private JSONArray getEdgeKeys(LogicalGraph graph) throws Exception {

    List<Tuple3<Set<String>, String, Boolean>> edgeKeys = graph.getEdges()
      .flatMap(new PropertyKeyMapper<>())
      .groupBy(1)
      .reduceGroup(new LabelGroupReducer())
      .collect();

    return buildArrayFromKeys(edgeKeys);
  }

  /**
   * Convenience method.
   * Takes a set of tuples of property keys and booleans, specifying if the property is numerical,
   * and creates a JSON array containing the same data.
   *
   * @param keys set of tuples of property keys and booleans, that are true if the property type
   *             is numerical
   * @return JSONArray containing the same data as the input
   * @throws JSONException if the construction of the JSON fails
   */
  private JSONArray buildArrayFromKeys(List<Tuple3<Set<String>, String, Boolean>> keys)
    throws JSONException {
    JSONArray keyArray = new JSONArray();
    for (Tuple3<Set<String>, String, Boolean> key : keys) {
      JSONObject keyObject = new JSONObject();
      JSONArray labels = new JSONArray();
      key.f0.forEach(labels::put);
      keyObject.put("labels", labels);
      keyObject.put("name", key.f1);
      keyObject.put("numerical", key.f2);
      keyArray.put(keyObject);
    }
    return keyArray;
  }

  /**
   * Compute the labels of the vertices.
   *
   * @param graph logical graph
   * @return JSONArray containing the vertex labels
   * @throws Exception if the computation fails
   */
  private JSONArray getVertexLabels(LogicalGraph graph) throws Exception {
    List<Set<String>> vertexLabels = graph.getVertices()
      .map(new LabelMapper<>())
      .reduce(new LabelReducer())
      .collect();

    if (vertexLabels.size() > 0) {
      return buildArrayFromLabels(vertexLabels.get(0));
    } else {
      return new JSONArray();
    }
  }

  /**
   * Compute the labels of the edges.
   *
   * @param graph logical graph
   * @return JSONArray containing the edge labels
   * @throws Exception if the computation fails
   */
  private JSONArray getEdgeLabels(LogicalGraph graph) throws Exception {
    List<Set<String>> edgeLabels = graph.getEdges()
      .map(new LabelMapper<>())
      .reduce(new LabelReducer())
      .collect();

    if (edgeLabels.size() > 0) {
      return buildArrayFromLabels(edgeLabels.get(0));
    } else {
      return new JSONArray();
    }
  }

  /**
   * Create a JSON array from the sets of labels.
   *
   * @param labels set of labels
   * @return JSON array of labels
   */
  private JSONArray buildArrayFromLabels(Set<String> labels) {
    JSONArray labelArray = new JSONArray();
    labels.forEach(labelArray::put);
    return labelArray;
  }

  /**
   * Get the complete graph in cytoscape-conform form.
   *
   * @param databaseName name of the database
   * @return Response containing the graph as a JSON, in cytoscape conform format.
   * @throws JSONException if JSON creation fails
   * @throws IOException   if reading fails
   */

  @POST
  @Path("/graph/{databaseName}")
  @Produces("application/json;charset=utf-8")
  public Response getGraph(@PathParam("databaseName") String databaseName) throws Exception {

    String path = getDatabaseURL(databaseName).getPath();

    CSVDataSource source = new CSVDataSource(path, config);

    LogicalGraph graph = source.getLogicalGraph();

    return createResponse(graph);
  }


  /**
   * Takes a {@link GroupingRequest}, executes a grouping with the parameters it contains and
   * returns the results as a JSON.
   *
   * @param request GroupingRequest send to the server, containing the parameters for a
   *                {@link Grouping}.
   * @return a JSON containing the result of the executed Grouping, a graph
   */
  @POST
  @Path("/grouping")
  @Produces("application/json;charset=utf-8")
  public Response computeGrouping(GroupingRequest request) {

    //load the database
    String databaseName = request.getDbName();

    String path = getDatabaseURL(databaseName).getPath();

    CSVDataSource source = new CSVDataSource(path, config);

    LogicalGraph graph = source.getLogicalGraph();

    //if no edges are requested, remove them as early as possible
    //else, apply the normal filters
    if (request.getFilterAllEdges()) {
      graph = graph.subgraph(new LabelFilter<>(request.getVertexFilters()),
        new AcceptNoneFilter<>());
    } else {
      graph = graph.subgraph(new LabelFilter<>(request.getVertexFilters()),
        new LabelFilter<>(request.getEdgeFilters()));
    }

    //construct the grouping with the parameters send by the request
    Grouping.GroupingBuilder builder = new Grouping.GroupingBuilder();
    int position;
    position = ArrayUtils.indexOf(request.getVertexKeys(), "label");
    if (position > -1) {
      builder.useVertexLabel(true);
      request.setVertexKeys((String[]) ArrayUtils.remove(request.getVertexKeys(), position));
    }
    builder.addVertexGroupingKeys(Arrays.asList(request.getVertexKeys()));

    position = ArrayUtils.indexOf(request.getEdgeKeys(), "label");
    if (position > -1) {
      builder.useEdgeLabel(true);
      request.setEdgeKeys((String[]) ArrayUtils.remove(request.getEdgeKeys(), position));
    }
    builder.addEdgeGroupingKeys(Arrays.asList(request.getEdgeKeys()));

    String[] vertexAggrFuncs = request.getVertexAggrFuncs();

    for (String vertexAggrFunc : vertexAggrFuncs) {
      String[] split = vertexAggrFunc.split(" ");
      switch (split[0]) {
        case "max":
          builder.addVertexAggregateFunction(new MaxVertexProperty(split[1], "max " + split[1]));
          break;
        case "min":
          builder.addVertexAggregateFunction(new MinVertexProperty(split[1], "min " + split[1]));
          break;
        case "sum":
          builder.addVertexAggregateFunction(new SumVertexProperty(split[1], "sum " + split[1]));
          break;
        case "count":
          builder.addVertexAggregateFunction(new VertexCount());
          break;
      }
    }

    String[] edgeAggrFuncs = request.getEdgeAggrFuncs();

    for (String edgeAggrFunc : edgeAggrFuncs) {
      String[] split = edgeAggrFunc.split(" ");
      switch (split[0]) {
        case "max":
          builder.addEdgeAggregateFunction(new MaxEdgeProperty(split[1], "max " + split[1]));
          break;
        case "min":
          builder.addEdgeAggregateFunction(new MinEdgeProperty(split[1], "min " + split[1]));
          break;
        case "sum":
          builder.addEdgeAggregateFunction(new SumEdgeProperty(split[1], "sum " + split[1]));
          break;
        case "count":
          builder.addEdgeAggregateFunction(new EdgeCount());
          break;
      }
    }

    // by default, we use the group reduce strategy
    builder.setStrategy(GroupingStrategy.GROUP_REDUCE);

    graph = builder.build().execute(graph);

    // specify the output collections
    return createResponse(graph);
  }

  @POST
  @Path("/sampling")
  @Produces("application/json;charset=utf-8")
  public Response computeSampling(SamplingRequest request) {

    //load the database
    String databaseName = request.getDbName();

    String path = getDatabaseURL(databaseName).getPath();

    CSVDataSource source = new CSVDataSource(path, config);

    LogicalGraph graph = source.getLogicalGraph();

    LogicalGraph result = null;

    switch (request.getSamplingType()) {
      case PAGE_RANK:
        result = runPageRankSamling(request, graph);
        break;
      case EDGE:
        result = runRandomEdgeSampling(request, graph);
        break;
      case LIMITED_DEGREE_VERTEX:
        result = runRandomLimitedDegreeVertexSampling(request, graph);
        break;
      case NON_UNIFORM_VERTEX:
        result = runRandomNonUniformVertexSampling(request, graph);
        break;
      case VERTEX_EDGE:
        result = runRandomVertexEdgeSampling(request, graph);
        break;
      case VERTEX_NEIGHBORHOOD:
        result = runRandomVertexNeighborhoodSampling(request, graph);
        break;
      case VERTEX:
        result = runRandomVertexSampling(request, graph);
        break;
      default:
        throw new IllegalArgumentException("Sampling type not recognized.");
    }

    assert result != null;
    return createResponse(result);
  }

  private LogicalGraph runRandomVertexSampling(SamplingRequest request, LogicalGraph graph) {
    return graph.sample(new RandomVertexSampling(
      Float.parseFloat(request.getProperty("sampling_threshold"))));
  }

  private LogicalGraph runRandomEdgeSampling(SamplingRequest request, LogicalGraph graph) {
    return graph.sample(new RandomEdgeSampling(
      Float.parseFloat(request.getProperty("sampling_threshold"))));
  }

  private LogicalGraph runRandomLimitedDegreeVertexSampling(SamplingRequest request, LogicalGraph graph) {
    VertexDegree vertexDegreeType;
    String vertexDegreeString = request.getProperty("vertex_degree_type");
    switch (vertexDegreeString) {
      case "in":
        vertexDegreeType = VertexDegree.IN;
        break;
      case "out":
        vertexDegreeType = VertexDegree.OUT;
        break;
      case "both":
        vertexDegreeType = VertexDegree.BOTH;
        break;
      default:
        throw new IllegalArgumentException("Degree type " + vertexDegreeString + " is not valid");
    }

    return graph.sample(new RandomLimitedDegreeVertexSampling(
      Float.parseFloat(request.getProperty("sampling_threshold")),
      Long.parseLong(request.getProperty("vertex_degree_threshold")),
      vertexDegreeType
    ));
  }

  private LogicalGraph runRandomNonUniformVertexSampling(SamplingRequest request, LogicalGraph graph) {
    return graph.sample(new RandomNonUniformVertexSampling(
      Float.parseFloat(request.getProperty("sampling_threshold"))));
  }

  private LogicalGraph runRandomVertexEdgeSampling(SamplingRequest request, LogicalGraph graph) {
    RandomVertexEdgeSampling.VertexEdgeSamplingType samplingType;
    String samplingTypeString = request.getProperty("vertex_edge_sampling_type");
    switch (samplingTypeString) {
      case "simple_version":
        samplingType = RandomVertexEdgeSampling.VertexEdgeSamplingType.SimpleVersion;
        break;
      case "nonuniform_version":
        samplingType = RandomVertexEdgeSampling.VertexEdgeSamplingType.NonuniformVersion;
        break;
      case "nonuniform_hybrid_version":
        samplingType = RandomVertexEdgeSampling.VertexEdgeSamplingType.NonuniformHybridVersion;
        break;
      default:
        throw new IllegalArgumentException(
          "Vertex edge ampling type " + samplingTypeString + " is not valid.");
    }
    return graph.sample(new RandomVertexEdgeSampling(
      Float.parseFloat(request.getProperty("vertex_sampling_threshold")),
      Float.parseFloat(request.getProperty("edge_sampling_threshold")),
      samplingType
    ));
  }

  private LogicalGraph runRandomVertexNeighborhoodSampling(SamplingRequest request, LogicalGraph graph) {
    Neighborhood neighborhood;
    String neighborhoodString = request.getProperty("vertex_neighborhood_type");
    switch (neighborhoodString) {
      case "in":
        neighborhood = Neighborhood.IN;
        break;
      case "out":
        neighborhood = Neighborhood.OUT;
        break;
      case "both":
        neighborhood = Neighborhood.BOTH;
        break;
      default:
        throw new IllegalArgumentException("Vertex neighborhood " + neighborhoodString + " is not valid.");
    }

    return graph.sample(new RandomVertexNeighborhoodSampling(
      Float.parseFloat(request.getProperty("sampling_threshold")),
      neighborhood
    ));
  }

  private LogicalGraph runPageRankSamling(SamplingRequest request, LogicalGraph graph) {
    return graph.sample(new PageRankSampling(
      Float.parseFloat(request.getProperty("dampening_factor")),
      Integer.parseInt(request.getProperty("max_iterations")),
      Float.parseFloat(request.getProperty("sampling_threshold")),
      request.getProperty("sample_greater_threshold").equals("true"),
      request.getProperty("keep_vertices_same_score").equals("true")));
  }


  private Response createResponse(GraphCollection graph) {
    List<GraphHead> resultHead = new ArrayList<>();
    List<Vertex> resultVertices = new ArrayList<>();
    List<Edge> resultEdges = new ArrayList<>();

    graph.getGraphHeads().output(new LocalCollectionOutputFormat<>(resultHead));
    graph.getVertices().output(new LocalCollectionOutputFormat<>(resultVertices));
    graph.getEdges().output(new LocalCollectionOutputFormat<>(resultEdges));

    return getResponse(resultHead, resultVertices, resultEdges);
  }

  private Response createResponse(LogicalGraph graph) {
    List<GraphHead> resultHead = new ArrayList<>();
    List<Vertex> resultVertices = new ArrayList<>();
    List<Edge> resultEdges = new ArrayList<>();

    graph.getGraphHead().output(new LocalCollectionOutputFormat<>(resultHead));
    graph.getVertices().output(new LocalCollectionOutputFormat<>(resultVertices));
    graph.getEdges().output(new LocalCollectionOutputFormat<>(resultEdges));

    return getResponse(resultHead, resultVertices, resultEdges);
  }

  private Response getResponse(List<GraphHead> resultHead, List<Vertex> resultVertices, List<Edge> resultEdges) {
    try {
      System.out.println("Starting Flink Job");
      ENV.execute();
      System.out.println("Finishing Flink Job");
      // build the response JSON from the collections
      String json = CytoJSONBuilder.getJSONString(resultHead, resultVertices, resultEdges);
      return Response.ok(json).build();

    } catch (Exception e) {
      e.printStackTrace();
      // if any exception is thrown, return an error to the client
      return Response.serverError().build();
    }
  }

  private URL getDatabaseURL(String dbName) {
    switch (dbName) {
      case "Small":
        return RequestHandler.class.getResource("/data/Small");
      case "Medium":
        return RequestHandler.class.getResource("/data/Medium");
      case "Big":
        return RequestHandler.class.getResource("/data/Big");
      default:
        throw new IllegalArgumentException("Database name " + dbName + " is invalid.");
    }
  }
}