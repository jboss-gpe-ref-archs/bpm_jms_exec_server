package com.redhat.gpe.kie.services.client.api;

import junit.framework.TestCase;
import static org.junit.Assert.*;

import java.net.URL;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.remoting.impl.netty.NettyConnectorFactory;
import org.hornetq.core.remoting.impl.netty.TransportConstants;
import org.hornetq.jms.client.HornetQConnectionFactory;
import org.hornetq.jms.client.HornetQQueue;
import org.junit.Test;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.services.client.serialization.JaxbSerializationProvider;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandResponse;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsRequest;
import org.kie.services.client.serialization.jaxb.impl.JaxbCommandsResponse;
import org.kie.services.client.serialization.jaxb.impl.process.JaxbProcessInstanceResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.drools.core.command.runtime.process.StartProcessCommand;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.acme.insurance.Driver;
import org.acme.insurance.Policy;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import java.io.StringWriter;
import java.io.ByteArrayInputStream;

public class LiveServerJMSTest extends TestCase {

    public static final String DEPLOYMENT_ID = "deploymentId";
    public static final String DEPLOYMENT_URL = "deployment.url";
    public static final String USER_ID = "userId";
    public static final String PASSWORD = "password";
    public static final String PROCESSID = "processId";
    public static final String DRIVER_NAME = "driverName";
    public static final String AGE = "age";
    public static final String POLICY_TYPE = "policyType";
    public static final String NUMBER_OF_ACCIDENTS = "numberOfAccidents";
    public static final String NUMBER_OF_TICKETS = "numberOfTickets";
    public static final String VEHICLE_YEAR = "vehicleYear";
    public static final String CREDIT_SCORE = "creditScore";
    public static final String DRIVER = "driver";
    public static final String POLICY = "policy";
    public static final String INCLUDE_POLICY_PAYLOAD = "includePolicyPayload";
    
    public static final String EXTRA_JAXB_CLASSES_PROPERTY_NAME = "extraJaxbClasses";
    public static final String DEPLOYMENT_ID_PROPERTY_NAME = "deploymentId";
    
    /* JMS connectin fields */
    private final JaxbSerializationProvider jaxbSerializationProvider = new JaxbSerializationProvider();
    
    private static final String KSESSION_QUEUE_NAME = "KIE.SESSION";
    private static final String RESPONSE_QUEUE_NAME = "KIE.RESPONSE";
    private static final long QUALITY_OF_SERVICE_THRESHOLD_MS = 5 * 1000;

    protected static Logger log = LoggerFactory.getLogger(LiveServerJMSTest.class);

    private String deploymentId;
    private URL deploymentUrl;
    private String userId;
    private String password;
    private String processId;

    private String name;
    private int age;
    private String policyType;
    private int numAccidents;
    private int numTickets;
    private int vehicleYear;
    private int creditScore;
    private boolean includePolicyPayload;
    
    private HornetQConnectionFactory _jmsConnectionFactory;
    private String _host = TransportConstants.DEFAULT_HOST;
    private int _port = TransportConstants.DEFAULT_PORT;

    public LiveServerJMSTest() throws Exception 
    {	
    	this.deploymentId = System.getProperty(DEPLOYMENT_ID, "git-playground");
	    this.deploymentUrl = new URL(System.getProperty(DEPLOYMENT_URL, "http://zareason:8330/kie-jbpm-services/"));
	    this.userId = System.getProperty(USER_ID, "jboss");
	    this.password = System.getProperty(PASSWORD, "brms");
	    this.processId = System.getProperty(PROCESSID, "simpleTask");
	
	    this.name = System.getProperty(DRIVER_NAME, "alex");
	    this.age = Integer.parseInt(System.getProperty(AGE, "21"));
	    this.policyType = System.getProperty(POLICY_TYPE, "MASTER");
	    this.numAccidents = Integer.parseInt(System.getProperty(NUMBER_OF_ACCIDENTS, "0"));
	    this.numTickets = Integer.parseInt(System.getProperty(NUMBER_OF_TICKETS, "1"));
	    this.vehicleYear = Integer.parseInt(System.getProperty(VEHICLE_YEAR, "2011"));
	    this.creditScore = Integer.parseInt(System.getProperty(CREDIT_SCORE, "800"));
	    this.includePolicyPayload = Boolean.parseBoolean(System.getProperty(INCLUDE_POLICY_PAYLOAD, "TRUE"));
	
	    StringBuilder sBuilder = new StringBuilder("system properties =");
	    sBuilder.append("\n\tdeploymentId : "+deploymentId);
	    sBuilder.append("\n\tdeploymentUrl : "+deploymentUrl);
	    sBuilder.append("\n\tuserId : "+userId);
	    sBuilder.append("\n\tpassword : "+password);
	    sBuilder.append("\n\tprocessId : "+processId);
	    sBuilder.append("\n\tdriverName : "+name);
	    sBuilder.append("\n\tage : "+age);
	    sBuilder.append("\n\npolicyType : " +policyType);
	    sBuilder.append("\n\t# accidents : "+numAccidents);
	    sBuilder.append("\n\t# tickets : "+numTickets);
	    sBuilder.append("\n\t# creditScore : "+creditScore);
	    sBuilder.append("\n\tvehicle year : "+vehicleYear);
	    sBuilder.append("\n\tincludePolicyPayload : "+includePolicyPayload);
	    log.info(sBuilder.toString());
	    
    }
    
    /**
     * Get {@link Queue} instance from core queue name.
     * @param name core queue name
     * @return {@link Queue} object
     */
    public static Queue getJMSQueue(final String name) {
        return new HornetQQueue(name);
    }

    
    private HornetQConnectionFactory getConnectionFactory()
    {
         Map<String, Object> params = new HashMap<String,Object>();
         params.put(TransportConstants.HOST_PROP_NAME, _host);
         params.put(TransportConstants.PORT_PROP_NAME, _port);
         _jmsConnectionFactory = new HornetQConnectionFactory(false, 
        		 new TransportConfiguration(NettyConnectorFactory.class.getName(), params));
         return _jmsConnectionFactory;
    }
   
    private JaxbCommandsResponse sendJmsJaxbCommandsRequest(String sendQueueName, JaxbCommandsRequest req, String USER,
            String PASSWORD) throws Exception {
    	HornetQConnectionFactory factory = getConnectionFactory();
        Queue jbpmQueue = getJMSQueue(sendQueueName);
        Queue responseQueue = getJMSQueue(RESPONSE_QUEUE_NAME);

        Connection connection = null;
        Session session = null;
        JaxbCommandsResponse cmdResponse = null;
        try {
            // setup
            connection = factory.createConnection(USER, PASSWORD);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            MessageProducer producer = session.createProducer(jbpmQueue);
            String corrId = UUID.randomUUID().toString();
            String selector = "JMSCorrelationID = '" + corrId + "'";
            MessageConsumer consumer = session.createConsumer(responseQueue, selector);

            connection.start();

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
            msg.setStringProperty(DEPLOYMENT_ID_PROPERTY_NAME, deploymentId);
            
            /* -------  Required for proper serialization on the Client side (for the JAXB context) ------- */
            jaxbSerializationProvider.addJaxbClasses(Policy.class);
            
            String xmlStr = jaxbSerializationProvider.serialize(req);
            msg.writeUTF(xmlStr);

            // send
            producer.send(msg);

            // receive
            Message response = consumer.receive(QUALITY_OF_SERVICE_THRESHOLD_MS);

            // check
            assertNotNull("Response is empty.", response);
            assertEquals("Correlation id not equal to request msg id.", corrId, response.getJMSCorrelationID());
            assertNotNull("Response from MDB was null!", response);
            xmlStr = ((BytesMessage) response).readUTF();
            cmdResponse = (JaxbCommandsResponse) jaxbSerializationProvider.deserialize(xmlStr);
            assertNotNull("Jaxb Cmd Response was null!", cmdResponse);
        } finally {
            if (connection != null) {
                connection.close();
                session.close();
            }
        }
        return cmdResponse;
    }
    
    /*
     * populate and return the Policy object to send
     */
    private Policy populatePolicyObject()
    {
    	Driver driverObj = new Driver();
        driverObj.setDriverName(name);
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
    
    //@Test
    public void testJMSTransformations()
    {
    	Policy policyObj = populatePolicyObject();
    	
    	// create JAXB context and instantiate marshaller
        JAXBContext context;
		try {
			context = JAXBContext.newInstance(Policy.class);
			 Marshaller m = context.createMarshaller();
		     m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
		     
		     // Write to Writer
		     StringWriter writer = new StringWriter();
		     m.marshal(policyObj, writer);
		     log.info(" Policy in XML: \n" + writer.toString());
		     
		     Unmarshaller um = context.createUnmarshaller();
		     Policy policyObj2 = (Policy) um.unmarshal(new ByteArrayInputStream(writer.toString().getBytes(Charset.forName("UTF-8"))));
		     
		     assertEquals("policy1 == policy2", policyObj.getVehicleYear(), policyObj2.getVehicleYear());
		        
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
       

       
    }
    
    @Test
    public void testRemoteJMSApi() {
      // create the start process command object
    	StartProcessCommand cmd = new StartProcessCommand(processId);
        
     // populate domain model classes
        Policy policyObj = populatePolicyObject();
        cmd.putParameter(POLICY, policyObj);
        
     // send the start process command
        JaxbCommandsRequest req = new JaxbCommandsRequest(deploymentId, cmd);
        JaxbCommandsResponse response = null;
		try {
			response = sendJmsJaxbCommandsRequest(KSESSION_QUEUE_NAME, req, userId, password);
		} catch (Exception e) {
			log.error("Error on sendJmsJaxbCommandsRequest: ", e );
			e.printStackTrace();
		}
        
     // check response
        assertNotNull("response was null.", response);
        assertTrue("response did not contain any command responses", response.getResponses() != null && response.getResponses().size() > 0);
        JaxbCommandResponse<?> cmdResponse = response.getResponses().get(0);
        assertTrue("response is not the proper class type : " + cmdResponse.getClass().getSimpleName(),cmdResponse instanceof JaxbProcessInstanceResponse);
        ProcessInstance procInst = (ProcessInstance) cmdResponse;
        long procInstId = procInst.getId();
        log.info("process Instance id: " + procInstId);
        log.info("JaxbCommandResponse: " + cmdResponse);
    	
    }

}
