package com.hunterjdev.mockjdbc;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * <code>TestAction</code> is the base class to extend in order to implement
 * the test code which will be run within the {@MockDriver} context. 
 * <code>TestAction</code> instances are used as arguments to the various
 * {@link MockDriver#execute(TestAction)} methods.
 * For example (here, as an anonymous class):
 * </p>
 * <pre>
 * MockDriver driver = new MockDriver( someMockResultSet );
 * mockDriver.execute( new TestAction("Some Slick Test") {
 *     &#064;Override
 *     public void runTest() {
 *         testSomething();
 *         ...
 *     }
 * });
 * </pre>
 * 
 * @author James Hunter
 * @version 1.0
 *
 */
public abstract class TestAction {
    private String name;
    private List<Method> calledMethods;

    public TestAction(String name) {
        this.name = name;
        this.calledMethods = new ArrayList<>();
    }

    /**
     * @return a list of <code>java.lang.reflect.Method</code> objects
     * representing all methods called during the execution of this
     * <code>TestAction</code>.
     */
    public List<Method> getCalledMethods() {
        return this.calledMethods;
    }

    /**
     * @param method
     */
    public void addCalledMethod(Method method) {
        this.calledMethods.add(method);
    }
    
    /**
     * 
     */
    public String getName() {
        return name;
    }
    
    /**
     * @param name
     */
    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        if (this.name != null && this.name.length() > 0) {
            return this.getName();
        }
        return super.toString();
    }

    /**
     * 
     */
    public abstract void runTest();
    
}