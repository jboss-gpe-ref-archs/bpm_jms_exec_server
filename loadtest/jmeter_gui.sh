
# Purpose
#  - start up the JMeter gui 
#  - uses the exact JMeter run-time and dependencies that will be used during execution of a test driven by jmeter-maven-plugin
#
# NOTE:  specify '-n' to execute the test harness in non-gui mode

java -Xms512M -Xmx512M -jar target/jmeter/bin/ApacheJMeter.jar -l target/jmeter/results/ref_arch.jtl -d target/jmeter -j target/jmeter/logs/ref_arch.jmx.log -t src/test/jmeter/ref_arch.jmx
