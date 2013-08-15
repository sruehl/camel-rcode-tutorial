/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apacheextras.camel.examples.rcode;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.csv.CsvDataFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author cemmersb
 */
public class RCodeRouteBuilder extends RouteBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(RCodeRouteBuilder.class);

  private File basePath;

  public RCodeRouteBuilder() {
    defaulBasePath();
  }

  public RCodeRouteBuilder(File basePath) {
    this.basePath = basePath;
  }

  public RCodeRouteBuilder(CamelContext context) {
    super(context);
    defaulBasePath();
  }

  private void defaulBasePath() {
    String path = System.getProperty("user.dir");
    basePath = new File(path + "./rcode-example/data");
  }

  @Override
  public void configure() throws Exception {
    configureCsvRoute();
  }

  private void configureCsvRoute() {
    CsvDataFormat csv = new CsvDataFormat();
    csv.setDelimiter(";");
    csv.setSkipFirstLine(true);

    from(basePath.toURI() + "?noop=TRUE")
            .process(new Processor() {
              @Override
              public void process(Exchange exchange) throws Exception {
                LOGGER.info("Exchange: {}", exchange);
              }
            })
            .log("Unmarshalling CSV file.")
            .unmarshal(csv)
            .to("log:CSV?level=INFO");
  }
}
