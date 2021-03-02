package one.entropy.archiver;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cdi.ContextName;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.properties.PropertiesComponent;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.ejb.Startup;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.jms.ConnectionFactory;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.kie.camel.KieCamelConstants.KIE_CLIENT;
import static org.kie.camel.KieCamelConstants.KIE_OPERATION;
import static org.kie.camel.KieCamelUtils.asCamelKieName;

@ApplicationScoped
@Startup
@ContextName("archiver")
public class PamArchiver extends RouteBuilder {

    private static final String IDS_HEADER = "PAM_IDs";

    @Resource(mappedName = "java:jboss/DefaultJMSConnectionFactory")
    private ConnectionFactory connectionFactory;

    @Inject
    @ContextName("archiver")
    private CamelContext camelContext;

    @PostConstruct
    public void start() {
        PropertiesComponent pc = new PropertiesComponent();
        pc.setLocation("archiver.properties");
        camelContext.addComponent("properties", pc);

        JmsComponent component = new JmsComponent();
        component.setConnectionFactory(connectionFactory);
        camelContext.addComponent("jms", component);
    }

    @Override
    public void configure() throws Exception {

        from("jms:queue:archive-request")
                .to("direct:process-archiver")
                .to("direct:node-archiver")
                .to("direct:variable-archiver")
                .process(this::prepareKieResponse)
                .toD("kie:http://{{login}}@localhost:8080/kie-server/services/rest/server");

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

    private void prepareKieResponse(Exchange exchange) {
        Map<String, Object> headers = exchange.getIn().getHeaders();
        headers.put(KIE_CLIENT, "process");
        headers.put(KIE_OPERATION, "signalProcessInstance");
        headers.put(asCamelKieName("containerId"), headers.get("KIE_DeploymentId"));
        headers.put(asCamelKieName("processInstanceId"), headers.get("KIE_ProcessInstanceId"));
        headers.put(asCamelKieName("signalName"), "archiver-response");
        headers.put(asCamelKieName("event"), "empty");
        exchange.getIn().setHeaders(headers);
    }
}
