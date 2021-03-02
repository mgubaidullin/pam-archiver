package one.entropy.archiver;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.component.properties.PropertiesComponent;

import javax.annotation.PostConstruct;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Startup
@ContextName("archiver")
public class PamArchiver extends RouteBuilder {

    private static final String IDS_HEADER = "PAM_IDs";

    @Inject
    @ContextName("archiver")
    private CamelContext camelContext;

    @PostConstruct
    public void start() {
        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("archiver.properties");
        camelContext.addComponent("properties", pc);
    }

    @Override
    public void configure() throws Exception {

        from("timer:archiver?period=1h&repeatCount=1")
                .to("direct:process-archiver")
                .to("direct:node-archiver")
                .to("direct:variable-archiver");

        from("direct:process-archiver")
                .to("sql:{{processes.select}}?dataSource=#mainDS")
                .process(exchange -> cacheIDsToArchive(exchange, "id"))
                .log("Processes to archive: ${header.CamelSqlRowCount}")
                .to("sql:{{processes.insert}}?batch=true&dataSource=#archiveDS")
                .log("Processes archived: ${header.CamelSqlUpdateCount}")
                .process(this::setIDsToDelete)
                .to("sql:{{processes.delete}}?batch=true&dataSource=#mainDS")
                .log("Processes deleted: ${header.CamelSqlUpdateCount}");

        from("direct:node-archiver")
                .to("sql:{{node.select}}?dataSource=#mainDS")
                .process(exchange -> cacheIDsToArchive(exchange, "id"))
                .log("Nodes to archive: ${header.CamelSqlRowCount}")
                .to("sql:{{node.insert}}?batch=true&dataSource=#archiveDS")
                .log("Nodes archived: ${header.CamelSqlUpdateCount}")
                .process(this::setIDsToDelete)
                .to("sql:{{node.delete}}?batch=true&dataSource=#mainDS")
                .log("Nodes deleted: ${header.CamelSqlUpdateCount}");

        from("direct:variable-archiver")
                .to("sql:{{variable.select}}?dataSource=#mainDS")
                .process(exchange -> cacheIDsToArchive(exchange, "id"))
                .log("Variables to archive: ${header.CamelSqlRowCount}")
                .to("sql:{{variable.insert}}?batch=true&dataSource=#archiveDS")
                .log("Variables archived: ${header.CamelSqlUpdateCount}")
                .process(this::setIDsToDelete)
                .to("sql:{{variable.delete}}?batch=true&dataSource=#mainDS")
                .log("Nodes deleted: ${header.CamelSqlUpdateCount}");
    }

    private void cacheIDsToArchive(Exchange exchange, String column) {
        List<Map<String, Object>> rows = exchange.getIn().getBody(List.class);
        List<Long> ids = rows.stream().map(map -> (Long) map.get(column)).collect(Collectors.toList());
        exchange.getIn().setHeader(IDS_HEADER, ids);
    }

    private void setIDsToDelete(Exchange exchange) {
        List<Long> ids = exchange.getIn().getHeader(IDS_HEADER, List.class);
        exchange.getIn().setBody(ids);
    }
}
