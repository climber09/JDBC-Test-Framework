----------------
## JDBC Test Framework

* Mock Object Testing
* Pure Java
* Lightweight
* Transparent
* Extendable

I designed the <em>JDBC Test Framework</em> for JUnit-style testing of JDBC code with mock objects. It allows your JDBC production code to be tested with mocked-up data. Mock objects are injected into the test execution at runtime to intercept calls to the database driver. The test writer is thus able to control the input of data, which would normally come from the database.

It is intended for those testing scenarios where it is difficult to separate the code which processes the incoming data from the code which executes the database query. The goal, then, is to test the data processing code in isolation from the database.

The mock objects are instances of <em>java.lang.reflect.Proxy</em>. They are substituted in place of the typical JDBC objects from the <em>java.sql</em> package, namely, <em>Driver</em>, <em>Connection</em>, <em>Statement</em> and its sub-classes, and <em>ResultSet</em>. The object substitution is completely transparent. There is no modification of production code. As long as your JDBC code can be called from within a test object, it can be tested with mock objects.

#### Example
Here is a simple JUnit-style test example:
```java
   public class MyMockResultSet{
       boolean isNext;

       public MyMockResultSet() {
           this.isNext = true;
       }

       public String getString(String colname) {
           return "myMockName";
       }

       public boolean next(){
           if (isNext) {
               isNext = false;
               return true;
           }
           return false;
       }
   }

    @Test
    public void testWithMockDB () throws Exception {
        TestAction test = new TestAction("MY AWESOME TEST") {
            @Override
            public void runTest() {
                MyJdbcObj myJdbcObj = new MyJdbcObj();
                String someResult = myJdbcObj.getSomeResult();
                assertEquals(expectedResult, someResult);
            }
        };
        MockDriver.execute(test, new MyMockResultSet());
    }
```

So, what this example shows is that there are two types of objects that the test writer needs to implement, usually. The first is a mock <em>ResultSet</em> type. Mock ResultSet objects are simple Java objects, which do not need to implement a specific interface. The only requirement for mock ResultSet objects is that they define methods which are identical to methods which are expected to be called on actual <em>java.sql.ResultSet</em> objects by the application code under test. These <em>mock methods</em> provide meaningful data which would otherwise come from the database.

The second object is the <em>TestAction</em> type, shown above as an anonymous class. <em>TestAction</em> is the base class to extend in order to implement the test code which will be run within <em>MockDriver</em>.

So as long as your JDBC code can be run from within a <em>TestAction</em>, then, that code can also be run within the <em>MockDriver</em> container. And the mock <em>ResultSet</em> object(s) that you provide will be substituted for the real <em>java.sql.ResultSet</em> which would normally provide data from the database.

Note that this approach is not intended to replace end-to-end testing or functional testing with an actual RDBMS.

Also note that since most of the typical database access code relies on a ResultSet object for data, there was very little consideration for supporting methods in <em>java.sql.Statement</em>, and its sub-classes, which return anything but a <em>java.sql.ResultSet</em>, such as, the various <em>Statement.executeXXX</em> methods. These methods usually execute some update to the database, rather than retrieve data, and sometimes return a boolean or int value indicating some result. But the return values for these methods would not be very meaningful for this type of testing. If a test writer needs to supply a mock method for one of these methods, this can be accomplished by extending <em>MockDriver</em> and overriding the <em>newProxyStatement(Class)</em> method, to provide a custom <em>java.lang.reflect InvocationHandler</em>. In fact, all of the <em>newProxyXXX</em> methods can be considered extension points to handle special cases.

While <em>MockDriver</em> was originally developed to test code which used the <em>DriverManager</em> to obtain a <em>Connection</em>, it can also be extended to handle different types of <em>Connection</em> sources, such as a <em>javax.sql.DataSource</em>. It takes a little more work to substitute a proxy for a real <em>DataSource</em>, but it is possible.

The one known limitation is the inability to create a proxy object which needs to be cast to a concrete type. The Java proxy mechanism can only create proxy objects from interfaces. JDBC code should be written to interfaces, anyway. This should be adopted in most cases, as a rule, as part of <em>best practices</em> for Java coding in general.

<span style="font-size: 0.85em;">Copyright &copy; 2017 James P Hunter</span>
