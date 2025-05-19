/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.logging.log4j.Logger;
import org.dspace.servicemanager.DSpaceKernelImpl;
import org.dspace.servicemanager.DSpaceKernelInit;
import org.dspace.services.RequestService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * DSpace Unit Tests need to initialize the DSpace Kernel / Service Mgr
 * in order to have access to configurations, etc. This Abstract class only
 * initializes the Kernel (without full in-memory DB initialization).
 * <P>
 * Tests which just need the Kernel (or configs) can extend this class.
 * <P>
 * Tests which also need an in-memory DB should extend AbstractUnitTest or AbstractIntegrationTest
 *
 * @author Tim
 * @see AbstractUnitTest
 * @see AbstractIntegrationTest
 */
@Ignore
@RunWith(MockitoJUnitRunner.class)
public class AbstractDSpaceTest {

    /**
     * Default constructor
     */
    protected AbstractDSpaceTest() { }

    /**
     * log4j category
     */
    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(AbstractDSpaceTest.class);

    /**
     * Test properties. These configure our general test environment
     */
    protected static Properties testProps;

    /**
     * DSpace Kernel. Must be started to initialize ConfigurationService and
     * any other services.
     */
    protected static DSpaceKernelImpl kernelImpl;

    /**
     * This method will be run before the first test as per @BeforeClass. It will
     * initialize shared resources required for all tests of this class.
     *
     * This method loads our test properties to initialize our test environment,
     * and then starts the DSpace Kernel (which allows access to services).
     */
    @BeforeClass
    public static void initKernel() {
        try {
            //set a standard time zone for the tests
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Dublin"));

            //load the properties of the tests
            testProps = new Properties();
            URL properties = AbstractDSpaceTest.class.getClassLoader()
                                                   .getResource("test-config.properties");
            testProps.load(properties.openStream());

            // Initialise the service manager kernel
            kernelImpl = DSpaceKernelInit.getKernel(null);
            if (!kernelImpl.isRunning()) {
                // NOTE: the "dspace.dir" system property MUST be specified via Maven
                // For example: by using <systemPropertyVariables> of maven-surefire-plugin or maven-failsafe-plugin
                kernelImpl.start(getDspaceDir()); // init the kernel
            }
            // Establish the request service startup
            RequestService requestService = kernelImpl.getServiceManager().getServiceByName(
                RequestService.class.getName(), RequestService.class);
            if (requestService == null) {
                throw new IllegalStateException(
                    "Could not get the DSpace RequestService to start the request transaction");
            }

            // Establish a request related to the current session
            // that will trigger the various request listeners
            requestService.startRequest();
        } catch (IOException ex) {
            log.error("Error initializing tests", ex);
            fail("Error initializing tests: " + ex.getMessage());
        }
    }


    /**
     * This method will be run after all tests finish as per @AfterClass. It
     * will clean resources initialized by the @BeforeClass methods.
     */
    @AfterClass
    public static void destroyKernel() throws SQLException {
        //we clear the properties
        testProps.clear();
        testProps = null;

        // ensure we close out the request (happy request)
        RequestService requestService = kernelImpl.getServiceManager().getServiceByName(
                RequestService.class.getName(), RequestService.class);
        requestService.endRequest(null);

        //Also clear out the kernel & nullify (so JUnit will clean it up)
        if (kernelImpl != null) {
            kernelImpl.destroy();
        }
        kernelImpl = null;
    }

    public static String getDspaceDir() {
        return System.getProperty("dspace.dir");

    }
}
