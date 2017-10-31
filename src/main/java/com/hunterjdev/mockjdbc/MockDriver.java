package com.hunterjdev.mockjdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log; 
import org.apache.commons.logging.LogFactory; 

/**
 * <code>MockDriver</code> provides a container to run and test JDBC code with
 * mock objects. Proxy objects are substituted for the typically used objects
 * within the <code>java.sql</code> package, namely <code>Driver</code>,
 * <code>Connection</code>, <code>Statement</code>, and <code>ResultSet</code>.
 * <p>
 * The primary design consideration for <code>MockDriver</code> is to give test
 * classes a mechanism for feeding data, from mock ResultSet objects, to the
 * application code under test. The mock ResultSet objects provide the input
 * that would normally be provided by the database, thus decoupling the test
 * code from dependency on the database. The testing code then tests the
 * application only. This approach is not intended to replace end-to-end
 * testing or functional testing with an actual RDBMS.
 * </p>
 * <p>
 * Mock ResultSet objects are simple Java Objects, which do not need to
 * implement a specific interface. The only requirement for mock ResultSet
 * objects is that they define methods which are identical to methods which
 * are expected to be called on actual <code>java.sql.ResultSet</code> objects
 * by the application code under test. These "mock methods" provide meaningful
 * data which would, otherwise come from the database.
 * </p>
 * <p>
 * {@link TestAction} instances are used to encapsulate the testing code
 * which needs to be run within the <code>MockDriver</code> context.
 * {@link TestAction} instances are specified as arguments to the 
 * {@link #execute(TestAction)} methods. For example (here, using an
 * anonymous {@link TestAction} type):
 * </p>
 * <pre>
 *     TestAction testAction = new TestAction("Some Awesome Test") {
 *         &#064;Override
 *         public void runTest() {
 *             testSomething();
 *             ...
 *         }
 *     });
 *     MockDriver driver = new MockDriver(mockResultSet);
 *     driver.setupProxyDriver();
 *     driver.execute(testAction);
 *     driver.resetJdbcDrivers();
 * </pre>
 * <p>
 * Since most of the typical database access code relies on a <code>ResultSet</code>
 * object for data, there was very little consideration for supporting methods in
 * <code>Statement</code>, and its sub-classes, which return anything but a
 * <code>ResultSet</code> - such as, the various Statement.executeXXX methods.
 * These methods usually execute some update to the database, rather than data
 * retrieval, and sometimes return a <code>boolean</code> or <code>int</code>
 * value indicating some result. But the return values for these methods are
 * rarely used. If a test writer needs to supply a mock method for one of these
 * methods, this can be accomplished by extending <code>MockDriver</code> and
 * overriding the {@link #newProxyStatement(Class)} method, to provide a custom
 * <code>java.lang.reflect InvocationHandler</code>. In fact, all of the
 * <code>newProxyXXX</code> methods can be considered as extension points to
 * handle special cases.
 * </p>
 * <p>
 * The one known limitation is the inability to create a proxy object which
 * needs to be cast to a concrete type. The Java proxy mechanism can only create
 * a proxy object from interfaces.
 * </p>
 *
 * @author James Hunter
 * @version 1.0
 *
 */
public class MockDriver {

    private static Log log = LogFactory.getLog(MockDriver.class);

    /**
     * Mock JDBC driver instance provided to calling code.
     */
    private Driver proxyDriver;

    /**
     * @see ResultSetProvider
     */
    private ResultSetProvider resultSetProvider;

    /**
     * Stored list of JDBC drivers found in the current JVM.
     */
    private List<java.sql.Driver> jdbcDrivers;

    /**
     *  Storage Map for the Class bindings.
     *  @see #getClassBindings()
     */
    private Map<Class<?>, Class<?>> classBindings;

    /**
     * The TestAction currently being run.
     */
    private TestAction currentTestAction;

    /**
     *
     */
    public MockDriver () {
        this.proxyDriver = newProxyDriver();
        this.initDefaultClassBindings();
    }

    /**
     *
     * @param mockResultSet Object which implements the mock methods invoked on
     *  a proxy ResultSet.
     */
    public MockDriver(Object mockResultSet) {
        this( new Object[] {mockResultSet} );
    }

    /**
     *
     * @param mockResultSets array of Objects which implement the mock methods
     *  invoked on a proxy ResultSet.
     */
    public MockDriver (Object[] mockResultSets) {
        this();
        this.setMockResultSets(mockResultSets);
    }

    /**
     *
     * @param mockResultSet Object which implements the mock methods invoked on
     *  a proxy ResultSet.
     */
    public void setMockResultSet(Object mockResultSet) {
        setMockResultSets(new Object[]{mockResultSet});
    }

    /**
     * @param mockResultSets array of Objects which implement the mock methods
     *  invoked on a proxy ResultSet.
     */
    public void setMockResultSets(Object[] mockResultSets) {
        if (this.resultSetProvider == null) {
            this.resultSetProvider = new DefaultResultSetProvider();
        }
        resultSetProvider.setResultSets(mockResultSets);
    }

    /**
     * @see #getClassBindings()
     */
    private void initDefaultClassBindings () {
        this.classBindings = new HashMap<>();
        classBindings.put(java.sql.Connection.class, java.sql.Connection.class);
        classBindings.put(java.sql.Statement.class, java.sql.Statement.class);
        classBindings.put(java.sql.PreparedStatement.class, java.sql.PreparedStatement.class);
        classBindings.put(java.sql.CallableStatement.class, java.sql.CallableStatement.class);
        classBindings.put(java.sql.ResultSet.class, java.sql.ResultSet.class);
    }

    /**
     * @see #getClassBindings()
     *
     * @param classBindings
     */
    public void setClassBindings(Map<Class<?>, Class<?>> classBindings) {
        this.classBindings = classBindings;
    }

    /**
     * Class bindings are stored in a <code>Map</code>. Class bindings are used
     * to bind a sub-type interface to its parent interface in order to provide
     * the correct interface type to the calling code, For example:
     * <pre>
     * com.mysql.jdbc.Statement stmt = (com.mysql.jdbc.Statement) connection.createStatement();
     * </pre>
     * The Java proxy mechanism will detect the return type above as a
     * <code>java.sql.Statement</code>. But the caller casts it to a sub-type.
     * If the proxy object is not built with the correct return type, a
     * <code>ClassCastException</code> is likely to occur. So, the class bindings,
     * then, supply the correct return type.
     *
     * @return the class bindings Map.
     */
    public Map<Class<?>, Class<?>> getClassBindings(){
        return this.classBindings;
    }

    /**
     * @see #getClassBindings()
     *
     * @param key
     * @param value
     */
    public void setClassBinding(Class<?> key, Class<?> value) {
        this.classBindings.put(key, value);
    }

    /**
     * Use this method to override the default {@link ResultSetProvider}.
     *
     * @param provider
     */
    public void setResultSetProvider(ResultSetProvider provider) {
        this.resultSetProvider = provider;
    }

    /**
     * This method must be called prior to {@link #execute(TestAction)}, so that
     * the proxy JDBC driver will be invoked, instead of any other drivers
     * registered with the <code>DriverManager</code>.
     *
     * @throws SQLException
     */
    public void setupProxyDriver() throws SQLException {
        this.jdbcDrivers = Collections.list(DriverManager.getDrivers());
        for (Driver driver : jdbcDrivers) {
            DriverManager.deregisterDriver(driver);
        }
        DriverManager.registerDriver(this.proxyDriver);
    }

    /**
     * This method must be invoked after the test action, in order to re-register
     * the original JDBC drivers with the <code>DriverManager</code>. It's up to
     * the test writer to know when the original drivers need to be restored.
     *
     * @throws SQLException
     */
    public void resetJdbcDrivers() throws SQLException {
        DriverManager.deregisterDriver(this.proxyDriver);
        for (Driver driver : this.jdbcDrivers) {
            DriverManager.registerDriver(driver);
        }
    }

    /**
     * @return the {@link ResultSetProvider}
     */
    public ResultSetProvider getResultSetProvider() {
        return this.resultSetProvider;
    }

    /**
     * This proxy type represents a <code>java.sql.Connection</code>. It is
     * made public for cases where a Connection object can be injected into the
     * code under test. Derived classes can override this method to supply a
     * custom <code>InvocationHandler</code>.
     *
     * @param type
     * @return a proxy object which can be caste to the class type which is
     * bound to the specified type.
     */
    public Object newProxyConnection(Class<?> type) {
        return this.createProxy( classBindings.get(type) );
    }

    /**
     * Derived classes can override this method to supply a custom
     * <code>InvocationHandler</code>.
     *
     * @return a proxy object as a <code>java.sql.Driver</code>.
     */
    protected Driver newProxyDriver() {
        return (Driver)this.createProxy(java.sql.Driver.class);
    }

    /**
     * Derived classes can override this method to supply a custom
     * <code>InvocationHandler</code>.
     *
     * @param type
     * @return a proxy object which can be caste to the class type which is
     * bound to the specified type.
     * .
     * @throws SQLException
     */
    protected Object newProxyStatement(Class<?> type) throws SQLException {
        return this.createProxy( classBindings.get(type) );
    }

    /**
     * Derived classes can override this method to supply a custom
     * <code>InvocationHandler</code>.
     *
     * @param type
     * @param mockObject
     * @return a proxy object which can be caste to the class type bound to the
     * specified type.
     */
    protected Object newProxyResultSet(Class<?> type, Object mockObject) {
        return this.createProxy(classBindings.get(type), mockObject);
    }

    /**
     * Creates a proxy object using the specified Class.
     *
     * @param type
     * @return a proxy object which can be caste to the Class type specified.
     */
    protected Object createProxy(Class<?> type) {
        return createProxy(type, null);
    }

    /**
     * Creates a proxy object using the specified Class.
     *
     * @param type
     * @param mockObject Object which implements methods which are expected to
     *  be called on the proxy object.
     *
     * @return a proxy object which can be caste to the Class type specified.
     */
    protected Object createProxy(Class<?> type, Object mockObject) {
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class[]{type},
                new MockObjectHandler(mockObject));
    }

    /**
     * Called to execute a particular test action within the mock driver
     * container. {@link #setupProxyDriver} must be called prior to this method.
     * {@link #resetJdbcDrivers} must be called before exiting the test case, or
     * before executing another test which depends on the actual system JDBC
     * drivers.
     *
     * @param testAction
     * @throws Exception
     */
    public void execute(TestAction testAction) throws Exception {
        log.info("Run TestAction - ".concat( testAction.toString() ));
        this.currentTestAction = testAction;
        testAction.runTest();
    }

    /**
     * Provided as a convenience, this method will take care of the substitution
     * of a mock JDBC driver for any other JDBC drivers in the JVM before the
     * test action, and then restore the original JDBC drivers after the test
     * action.
     *
     * @param testAction
     * @param mockResultSet
     * @throws Exception
     */
    public static void execute(TestAction testAction, Object mockResultSet)
            throws Exception {
        execute(testAction, new Object[]{ mockResultSet });
    }

    /**
     * Provided as a convenience, this method will take care of the substitution
     * of a mock JDBC driver for any other JDBC drivers in the JVM before the
     * test action, and then restore the original JDBC drivers after the test
     * action.
     *
     * @param testAction
     * @param mockResultSets
     * @throws SQLException
     */
    public static void execute(TestAction testAction, Object[] mockResultSets)
            throws Exception {

        MockDriver mockDriver = new MockDriver(mockResultSets);

        // First thing, unregister the current JDBC drivers so
        // they won't be invoked.
        mockDriver.setupProxyDriver();

        mockDriver.execute(testAction);

        // Now it's important to clean up and re-register the saved
        // JDBC drivers.
        mockDriver.resetJdbcDrivers();
    }

    public static Class<MockObjectHandler> getMockObjectHandler() {
        return MockObjectHandler.class;
    }

    /**
     * <code>MockObjectHandler</code> is the primary controller and invocation
     * handler for proxy objects. It's primary responsibility is to return
     * appropriately typed proxy objects corresponding to the commonly used
     * JDBC objects - <code>Connection</code>, <code>Statement</code>,
     * and <code>ResultSet</code>.
     *
     */
    protected class MockObjectHandler implements InvocationHandler {

        /**
         * Object which implements methods which are expected to be called on
         * the proxy bound to this <code>InvocationHandler</code>.
         */
        private Object mockObject;

        public MockObjectHandler() {
        }

        public MockObjectHandler(Object mockObject) {
            this.mockObject = mockObject;
        }

        /**
         * Determines if there is a public method declared in the supplied
         * Class type which can be invoked.
         *
         * @param type
         * @param method
         * @return
         */
        private boolean methodIsAvailable(Class<?> type, Method method) {
            List<Method> methodList = Arrays.asList( type.getMethods() );
            return methodList.contains(method);
        }

        /**
         * Determines the type of object called for, and calls the corresponding
         * newProxyXXX method to acquire the appropriate return type. When
         * methods are invoked on a ResultSet, the supplied mock object, will
         * be used to answer the call.
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args)
                throws Throwable {
            if(currentTestAction != null) {
                currentTestAction.addCalledMethod(method);
            }
            
            Class<?> returnType = method.getReturnType();

            // Answer a call for a Connection with a proxy.
            if (Connection.class.isAssignableFrom(returnType)) {
                log.info( "returning ".concat( returnType.toString() ));
                return newProxyConnection(returnType);
            }
            // Answer a call for a Statement with a proxy.
            if (Statement.class.isAssignableFrom(returnType)) {
                log.info("returning ".concat( returnType.toString() ));
                return newProxyStatement(returnType);
            }
            // Answer a call for a ResultSet with a proxy.
            if (ResultSet.class.isAssignableFrom(returnType)) {
                Object mockObject = resultSetProvider.getResultSet();
                log.info("returning ".concat( mockObject.getClass().toString() ));
                return newProxyResultSet(returnType, mockObject);
            }
            // Answer ResultSet invocations with a mock object call.
            if (ResultSet.class.isAssignableFrom( method.getDeclaringClass() )) {
                if (mockObject == null) {
                    throw new RuntimeException(
                        "No pre-assigned mock ResultSet object for this action");
                }
                // If the method called is not implemented in the mock object,
                // then a NoSuchMethodException will be thrown.
                method = mockObject.getClass().getMethod(
                        method.getName(),
                        method.getParameterTypes());
                log.info("Invocation on mock object: ".concat( method.toString() ));
                return method.invoke(mockObject, args);
            }
            // try to answer other calls, such as toString().
            if (methodIsAvailable(getClass(), method)) {
                return method.invoke(this, args);
            }
            // For all other method calls, which don't need to be mimicked
            // or cannot be.
            log.info("Mock object cannot invoke ".concat( method.toString() ));
            return null;
        }
    }
}
