# HttpContentRepository
HTTP Content Repository Servlet for Filenet P8 and SAP

Important notes:

1) Please don't forget to customize your SAP system with OAC0, OAC2, OAC3 transactions

2) In your cataline home directory Jace.jar, log4j.jar, stax-api.jar, xlxpScanner.jar, xlxpScannerUtils.jar files must be deployed

3) Include class files after compilation of the sources in ContentRepositoryFN folder created in your webapps directory

4) Sample web.xml file included for deploying the servlet to Tomcat

5) dispatcherBR version was developed to be able to respond to byte range requests. This is especially valuable to use the repository to stream multimedia content to HTML5 video and audio elements.
