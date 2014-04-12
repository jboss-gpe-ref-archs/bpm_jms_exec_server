package com.redhat.gpe.refarch.bpm_jms_exec_server.processTier;

import java.io.Serializable;
import java.util.UUID;

import javax.naming.Context;
import javax.naming.InitialContext;

import org.kie.api.runtime.process.WorkItem;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.process.WorkItemHandler;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.acme.insurance.Policy;
import org.acme.insurance.Driver;

public class JMSWorkItemHandler implements WorkItemHandler {
    
    private static final String QUEUE_NAME = "queue.name";
    private static final String CONNECTION_FACTORY_NAME = "connection.factory.name";
    private static final Logger log = LoggerFactory.getLogger(JMSWorkItemHandler.class);

    private static Connection connection;
    private static Queue queue;

    private boolean transacted = false;
    
    public JMSWorkItemHandler() {}

    public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
        try {
            if(connection == null){
                String cFactoryName = (String)workItem.getParameter(CONNECTION_FACTORY_NAME);
                String qName = (String)workItem.getParameter(QUEUE_NAME);
                getJMSObjects(cFactoryName, qName);
            }

            Policy policy = (Policy)workItem.getParameter("policy");
            Driver driver = policy.getDriver();

            // Do some work

            log.info("Sending messageContent: " + driver);
            sendMessage(driver);

            // notify manager that work item has been completed
            manager.completeWorkItem(workItem.getId(), null);
        }catch(Throwable x) {
            x.printStackTrace();
        }
    }

    public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
        log.warn("abortWorkItem() what should be the biz-logic here ?");
    }

    private synchronized static void getJMSObjects(String cFactoryName, String qName) throws Exception {
        if(connection != null)
            return;

        Context jndiContext = null;
        try {
        	log.info("getJMSObjects() cFactoryName = "+cFactoryName+" : qName = "+qName);
            jndiContext = new InitialContext();
            ConnectionFactory cFactory = (ConnectionFactory)jndiContext.lookup(cFactoryName);
            connection = cFactory.createConnection();
            queue = (Queue)jndiContext.lookup(qName);
        }finally {
            jndiContext.close();
        }
    }
    
    protected void sendMessage(Serializable messageContent) throws JMSException {
        Session session = null;
        try {
            session = connection.createSession(transacted, Session.AUTO_ACKNOWLEDGE);
            
            MessageProducer producer = session.createProducer(queue);
            ObjectMessage msg = session.createObjectMessage(messageContent);
            producer.send(msg);
        } finally {
            if (session != null) {
                session.close();
            }
            
        }
    }
}
