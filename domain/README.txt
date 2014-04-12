instructions:
  1)  mvn clean install
  2)  cp -r conf/org $JBOSS_HOME/modules/system/layers/base
  3)  cp target/domain-1.0.jar $JBOSS_HOME/modules/system/layers/base/org/acme/insurance/main
  4)  add an explicit dependency on this static module

        ....
        <dependencies>
            <module name="org.acme.insurance" export="true"/>
        </dependencies
        ...

  5)  bounce the JBoss EAP JVM
