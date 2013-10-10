/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apacheextras.camel.examples.rcode.builder;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.csv.CsvDataFormat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apacheextras.camel.examples.rcode.processor.MonthlySalesFigureCalcProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author cemmersb, Sebastian Rühl
 */
public class RCodeRouteBuilder extends RouteBuilder {
  
  /** Logger provides some degree of debugging information. */
  private final static Logger LOGGER = LoggerFactory.getLogger(RCodeRouteBuilder.class);
  
  /** Map contains all the R code which has been loaded via external files. */
  private final static Map<String, String> R_CODE_SOURCES = new HashMap<String, String>();
  static {
    R_CODE_SOURCES.put("FN_PLOT_HOLT_WINTERS_FORECAST", sourceRCodeSources("fn_PlotHoltWintersForecast.R"));
    R_CODE_SOURCES.put("CMD_LIBRARIES", sourceRCodeSources("cmd_Libraries.R"));
    R_CODE_SOURCES.put("CMD_TIME_SERIES", sourceRCodeSources("cmd_TimeSeries.R"));
    R_CODE_SOURCES.put("CMD_DEVICE", sourceRCodeSources("cmd_Device.R"));
    R_CODE_SOURCES.put("CMD_PLOT", sourceRCodeSources("cmd_Plot.R"));
    R_CODE_SOURCES.put("CMD_BINARY", sourceRCodeSources("cmd_Binary.R"));
  }
  
  private File source;
  private File target;
  private static final String DIRECT_CSV_SINK_URI = "direct://csv_sink";
  private static final String DIRECT_RCODE_SOURCE_URI = "direct://rcode_source";
  private static final String DIRECT_GRAPH_FILE_SOURCE_URI = "seda://graph_file_source";
  private static final String DIRECT_GRAPH_JSON_SOURCE_URI = "seda://graph_json_source";

  public RCodeRouteBuilder(File source, File target) {
    this.source = source;
    this.target = target;
  }
  
  /**
   * Reads the R code sources based on the given source path within the class 
   * path. Returns the result as String that can be further used within the 
   * route.
   * @param rCodeSource - String value of of the resource within the class loader
   * @return read sources as String value
   */
  private static String sourceRCodeSources(String rCodeSource) {
    // StringWriter to convert the InputStream to String
    final StringWriter writer = new StringWriter();
    
    if(LOGGER.isDebugEnabled()) {
      LOGGER.debug("Try to source the following R Code snipped: {}", rCodeSource);
    }
    // Sourcing the external file and read the UTF-8 encoded String
    try {
      InputStream inputStream = RCodeRouteBuilder.class.getResourceAsStream(rCodeSource);
      IOUtils.copy(inputStream, writer, CharEncoding.UTF_8);
    } catch (IOException ex) {
      LOGGER.error("Could not copy InputStream on to StringWriter: {}", ex);
    }
    // Return the R code sources
    return writer.toString();
  }
  
  @Override
  public void configure() throws Exception {
    configureCsvRoute();
    configureRCodeRoute();
    configureGraphFileRoute();
    configureGraphJsonRoute();
    wireRoutes();
  }

  private void configureGraphJsonRoute() {
    // TODO: Export the binary file in a JSON rendert object and write to output folder
    from(DIRECT_GRAPH_JSON_SOURCE_URI)
        // TODO: missing JSON conversion implementation
        //.to("log://graph_json?level=INFO"); // prints currently some awkward byte code
        .log("JSON graph generated")
        .end();
  }

  /**
   * Takes an input as bytes and writes it as an jpeg file.
   */
  private void configureGraphFileRoute() {
    from(DIRECT_GRAPH_FILE_SOURCE_URI)
        .setHeader(Exchange.FILE_NAME, simple("graph${exchangeId}.jpeg"))
        .to("file://" + target.getAbsolutePath())
        .log("Generated graph file: ${header.CamelFileNameProduced}")
        .end();
  }

  /**
   * Takes an incoming string argument containing monthly quantities and
   * generates an output graph.
   */
  private void configureRCodeRoute() {

    from(DIRECT_RCODE_SOURCE_URI)
        .setBody(
        simple(R_CODE_SOURCES.get("CMD_LIBRARIES") + "\n"
        + R_CODE_SOURCES.get("FN_PLOT_HOLT_WINTERS_FORECAST") + "\n"
        + "sales <- c(${body});\n"
        + R_CODE_SOURCES.get("CMD_TIME_SERIES") + "\n"
        + R_CODE_SOURCES.get("CMD_DEVICE") + "\n"
        + R_CODE_SOURCES.get("CMD_PLOT") + "\n"
        + R_CODE_SOURCES.get("CMD_BINARY") + "\n"))
        .to("log://command?level=INFO")
        .to("rcode://localhost:6311/parse_and_eval?bufferSize=4194304")
        .to("log://r_output?level=INFO")
        .setBody(simple("${body.asBytes}"))
        .end();
  }

  /**
   * Configures a CSV route that reads the quantity values from the route and
   * sends the result to the RCode route.
   */
  private void configureCsvRoute() {
    // Configure CSV data format with ';' as separator and skipping of the header
    final CsvDataFormat csv = new CsvDataFormat();
    csv.setDelimiter(";");
    csv.setSkipFirstLine(true);
    from("file://" + source.getPath() + "?noop=TRUE")
        .log("Unmarshalling CSV file.")
        .unmarshal(csv)
        .to("log://CSV?level=DEBUG")
        .process(new MonthlySalesFigureCalcProcessor())
        .to("log://CSV?level=INFO")
        .log(LoggingLevel.INFO, "Finished the unmarshaling")
        .to(DIRECT_CSV_SINK_URI)
        .end();
  }

  /**
   * Wires together the routes.
   */
  private void wireRoutes() {
    from(DIRECT_CSV_SINK_URI)
        .to(DIRECT_RCODE_SOURCE_URI)
        .multicast()
        .to(DIRECT_GRAPH_FILE_SOURCE_URI, DIRECT_GRAPH_JSON_SOURCE_URI)
        .end();
  }
}
