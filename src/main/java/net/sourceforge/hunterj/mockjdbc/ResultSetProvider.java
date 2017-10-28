package net.sourceforge.hunterj.mockjdbc;

/**
 * <code>ResultSetProvider</code> is used to encapsulate the logic needed to
 * provide mock ResultSet objects to {@link MockDriver} in cases where
 * multiple ResultSet objects are needed by the code under test. Currently,
 * the only type of injection process which can be supported is a predictable
 * sequential operation. Test cases need to be deterministic so that the mock
 * objects can be supplied in the correct sequence.
 *
 * @author James Hunter
 * @version 1.0
 * 
 */
public interface ResultSetProvider {
    public Object getResultSet();
    public void setResultSets(Object[] resultSets);
}
