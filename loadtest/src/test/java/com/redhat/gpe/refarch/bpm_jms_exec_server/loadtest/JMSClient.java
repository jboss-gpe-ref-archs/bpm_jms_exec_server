package com.redhat.gpe.refarch.bpm_jms_exec_server.loadtest;

import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import javax.jms.*;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

import org.apache.log4j.xml.DOMConfigurator;
import org.apache.log4j.Logger;

import org.kie.api.runtime.process.ProcessInstance;
import org.kie.services.client.serialization.JaxbSerializationProvider;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsRequest;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsResponse;
import org.kie.services.client.serialization.jaxb.impl.process.JaxbProcessInstanceResponse;
import org.drools.core.command.runtime.process.StartProcessCommand;

import org.kie.services.client.serialization.jaxb.rest.JaxbExceptionResponse;

import org.acme.insurance.Policy;
import org.acme.insurance.Driver;

public final class JMSClient extends AbstractJavaSamplerClient {

    private static final String PATH_TO_LOG4J_CONFIG = "path.to.log4j.xml";
    private static final String SUCCESS_MESSAGE = "**success**";

    private static final String KSESSION_QUEUE_NAME = "ksession.queue.name";
    private static final String RESPONSE_QUEUE_NAME = "response.queue.name";
    private static final String DEPLOYMENT_ID = "deploymentId";
    private static final String PROCESS_ID = "process.id";

    public static final String DRIVER_NAME = "driverName";
    public static final String AGE = "age";
    public static final String POLICY_TYPE = "policyType";
    public static final String NUMBER_OF_ACCIDENTS = "numberOfAccidents";
    public static final String NUMBER_OF_TICKETS = "numberOfTickets";
    public static final String VEHICLE_YEAR = "vehicleYear";
    public static final String CREDIT_SCORE = "creditScore";
    public static final String DRIVER = "driver";
    public static final String POLICY = "policy";

    private static final long QUALITY_OF_SERVICE_THRESHOLD_MS = 5 * 1000;
    public static final String EXTRA_JAXB_CLASSES_PROPERTY_NAME = "extraJaxbClasses";

    
    private static IJMSClientProvider jmsProvider = new HornetQClientProvider();
    private static Logger log = Logger.getLogger("JMSClient");
    private static Connection connection = null;
    private static Queue ksessionQueue = null;
    private static Queue responseQueue = null;
    private static String processId;
    private static String deploymentId;

    private String testName;

    private String driverName;
    private int age;
    private String policyType;
    private int numAccidents;
    private int numTickets;
    private int vehicleYear;
    private int creditScore;
    private Policy policyObj;
    private JaxbSerializationProvider jaxbSerializationProvider;

    // obviously gets invoked a single time per JVM
    static{
        String pathToLog4jConfig = System.getProperty(PATH_TO_LOG4J_CONFIG);
        if(pathToLog4jConfig != null && !pathToLog4jConfig.equals("")) {
            DOMConfigurator.configure(pathToLog4jConfig);
        }
        try {
            connection = jmsProvider.getConnection();
            String ksessionQueueName = System.getProperty(KSESSION_QUEUE_NAME, "KIE.SESSION");
            String responseQueueName = System.getProperty(RESPONSE_QUEUE_NAME, "KIE.RESPONSE");
            ksessionQueue = jmsProvider.getQueue(ksessionQueueName);
            responseQueue = jmsProvider.getQueue(responseQueueName);

            processId = System.getProperty(PROCESS_ID, "simpleTask");
            deploymentId = System.getProperty(DEPLOYMENT_ID);

        }catch(Exception x) {
            throw new RuntimeException(x);
        }
    }

    // gets invoked a single time for each concurrent client
    public void setupTest(JavaSamplerContext context){
        testName = context.getParameter(TestElement.NAME);

        driverName = System.getProperty(DRIVER_NAME, "alex");
        age = Integer.parseInt(System.getProperty(AGE, "21"));
        policyType = System.getProperty(POLICY_TYPE, "MASTER");
        numAccidents = Integer.parseInt(System.getProperty(NUMBER_OF_ACCIDENTS, "0"));
        numTickets = Integer.parseInt(System.getProperty(NUMBER_OF_TICKETS, "1"));
        vehicleYear = Integer.parseInt(System.getProperty(VEHICLE_YEAR, "2011"));
        creditScore = Integer.parseInt(System.getProperty(CREDIT_SCORE, "800"));

        StringBuilder sBuilder = new StringBuilder("system properties =");
        sBuilder.append("\n\tdeploymentId : "+deploymentId);
        sBuilder.append("\n\tprocessId : "+processId);
        sBuilder.append("\n\tdriverName : "+driverName);
        sBuilder.append("\n\tage : "+age);
        sBuilder.append("\n\npolicyType : " +policyType);
        sBuilder.append("\n\t# accidents : "+numAccidents);
        sBuilder.append("\n\t# tickets : "+numTickets);
        sBuilder.append("\n\t# creditScore : "+creditScore);
        sBuilder.append("\n\tvehicle year : "+vehicleYear);
        log.info(sBuilder.toString());

        // based on given props for this client, create policyObj 
        policyObj = populatePolicyObject();

        jaxbSerializationProvider = new JaxbSerializationProvider();
    }

    public SampleResult runTest(JavaSamplerContext context){
        SampleResult result = new SampleResult();
        result.setSampleLabel(testName);
        try {
            result.sampleStart();

            // create the start process command object
            StartProcessCommand cmd = new StartProcessCommand(processId);

            // populate domain model classes
            cmd.putParameter(POLICY, policyObj);

            // send the start process command
            JaxbCommandsRequest jaxbRequest = new JaxbCommandsRequest(deploymentId, cmd);
            JaxbCommandsResponse jaxbResponse = sendJmsJaxbCommandsRequest(jaxbRequest);

            // parse response for processInstanceId
            JaxbCommandResponse<?> cmdResponse = jaxbResponse.getResponses().get(0);
            if(cmdResponse != null && (cmdResponse instanceof JaxbExceptionResponse)) {
                log.error("runTest() cmdResponse = "+((JaxbExceptionResponse)cmdResponse).getStackTrace());
                result.setResponseMessage(((JaxbExceptionResponse)cmdResponse).getStackTrace());
                result.setSuccessful(false);
            }else {
                ProcessInstance procInst = (ProcessInstance) cmdResponse;
                long pInstanceId = procInst.getId();
                log.info("runTest() just created new process instance with id = "+pInstanceId);
                result.setResponseMessage(SUCCESS_MESSAGE+" : pInstanceId = "+pInstanceId);
                result.setSuccessful(true);
                result.setResponseCodeOK();
            }

        }catch(Throwable x){
            StringWriter sw = new StringWriter();
            x.printStackTrace(new PrintWriter(sw));
            String stackTrace = sw.toString();
            log.error("runTest() stackTrace = "+stackTrace);
            result.setResponseMessage(stackTrace);
            result.setSuccessful(false);
        }
        return result;
    }
    
    private JaxbCommandsResponse sendJmsJaxbCommandsRequest(JaxbCommandsRequest jaxbRequest) throws Exception {
        Session session = null;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            final MessageProducer producer = session.createProducer(ksessionQueue);
            String corrId = UUID.randomUUID().toString();
            String selector = "JMSCorrelationID = '" + corrId + "'";
            MessageConsumer consumer = session.createConsumer(responseQueue, selector);

            // Create msg
            BytesMessage msg = session.createBytesMessage();
            msg.setJMSCorrelationID(corrId);
            msg.setIntProperty("serialization", JaxbSerializationProvider.JMS_SERIALIZATION_TYPE);

            /* ----- Required for deserialization on the server ---------- */
            Set<Class<?>> extraJaxbClasses = new HashSet<Class<?>>();
            extraJaxbClasses.add(Policy.class);
            String extraJaxbClassesPropertyValue = JaxbSerializationProvider.classSetToCommaSeperatedString(extraJaxbClasses);
            msg.setStringProperty(EXTRA_JAXB_CLASSES_PROPERTY_NAME, extraJaxbClassesPropertyValue);

            /* ------- Required for the server to locate the target deployment for the process --------- */
            msg.setStringProperty(DEPLOYMENT_ID, deploymentId);

            /* -------  Required for proper serialization on the Client side (for the JAXB context) ------- */
            jaxbSerializationProvider.addJaxbClasses(Policy.class);

            String xmlStr = jaxbSerializationProvider.serialize(jaxbRequest);
            msg.writeUTF(xmlStr);

            producer.send(msg);

            // receive
            Message response = consumer.receive(QUALITY_OF_SERVICE_THRESHOLD_MS);

            xmlStr = ((BytesMessage) response).readUTF();
            JaxbCommandsResponse cmdResponse = (JaxbCommandsResponse) jaxbSerializationProvider.deserialize(xmlStr);
            return cmdResponse;
        } finally {
            if(session != null)
                session.close();
        }
    }

    private Policy populatePolicyObject() {
        Driver driverObj = new Driver();
        driverObj.setDriverName(driverName);
        driverObj.setAge(age);
        driverObj.setNumberOfAccidents(numAccidents);
        driverObj.setNumberOfTickets(numTickets);
        driverObj.setCreditScore(creditScore);
        driverObj.setSsn("555-55-555");
        driverObj.setDlNumber("7");
        Policy policyObj = new Policy();
        policyObj.setVehicleYear(vehicleYear);
        policyObj.setDriver(driverObj);
        policyObj.setPolicyType(policyType);
        return policyObj;
    }

}
