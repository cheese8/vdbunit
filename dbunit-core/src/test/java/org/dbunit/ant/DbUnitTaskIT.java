/*
 *
 * The DbUnit Database Testing Framework
 * Copyright (C)2002-2004, DbUnit.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */

package org.dbunit.ant;

import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.tools.ant.BuildEvent;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.BuildListener;
import org.apache.tools.ant.MagicNames;
import org.apache.tools.ant.MagicTestNames;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.UnknownElement;
import org.apache.tools.ant.util.ProcessUtil;
import org.dbunit.DatabaseEnvironmentLoader;
import org.dbunit.DatabaseUnitException;
import org.dbunit.IDatabaseTester;
import org.dbunit.database.DatabaseConfig;
import org.dbunit.database.IDatabaseConnection;
import org.dbunit.dataset.FilteredDataSet;
import org.dbunit.dataset.IDataSet;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.NoSuchTableException;
import org.dbunit.dataset.datatype.IDataTypeFactory;
import org.dbunit.ext.mssql.InsertIdentityOperation;
import org.dbunit.ext.oracle.OracleDataTypeFactory;
import org.dbunit.operation.DatabaseOperation;
import org.dbunit.testutil.TestUtils;
import org.dbunit.util.FileHelper;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import junitx.framework.ArrayAssert;

/**
 * Ant-based test class for the Dbunit ant task definition.
 *
 * @author Timothy Ruppert
 * @author Ben Cox
 * @author Last changed by: $Author$
 * @version $Revision$ $Date$
 * @since Jun 10, 2002
 * @see org.dbunit.ant.AntTest
 */
public class DbUnitTaskIT extends TestCase {

    static protected Class classUnderTest = DbUnitTaskIT.class;

    private static final String BUILD_FILE_DIR = "xml";
    private static final String OUTPUT_DIR = "target/xml";

    private File outputDir;

    public DbUnitTaskIT(String name) {
	super(name);
    }

    @Override
    public void setUp() throws Exception {
	// This line ensure test database is initialized
	DatabaseEnvironmentLoader.getInstance(null);

	String filePath = BUILD_FILE_DIR + "/antTestBuildFile.xml";
	assertTrue("Buildfile not found", TestUtils.getFile(filePath).isFile());
	configureProject(TestUtils.getFileName(filePath));

	outputDir = new File(getProjectDir(), OUTPUT_DIR);
	outputDir.mkdirs();
    }

    @Override
    protected void tearDown() throws Exception {
	super_tearDown();

	outputDir = new File(getProjectDir(), OUTPUT_DIR);
	FileHelper.deleteDirectory(outputDir);
    }

    public void testNoDriver() {
	expectBuildException("no-driver", "Should have required a driver attribute.");
    }

    public void testNoDbUrl() {
	expectBuildException("no-db-url", "Should have required a url attribute.");
    }

    public void testNoUserid() {
	expectBuildException("no-userid", "Should have required a userid attribute.");
    }

    public void testNoPassword() {
	expectBuildException("no-password", "Should have required a password attribute.");
    }

    public void testInvalidDatabaseInformation() {
	Throwable sql = null;
	try {
	    executeTarget("invalid-db-info");
	} catch (BuildException e) {
	    sql = e.getException();
	} finally {
	    assertNotNull("Should have thrown a SQLException.", sql);
	    assertTrue("Should have thrown a SQLException.", (sql instanceof SQLException));
	}
    }

    public void testInvalidOperationType() {
	Throwable iae = null;
	try {
	    executeTarget("invalid-type");
	} catch (BuildException e) {
	    iae = e.getException();
	} finally {
	    assertNotNull("Should have thrown an IllegalArgumentException.", iae);
	    assertTrue("Should have thrown an IllegalArgumentException.", (iae instanceof IllegalArgumentException));
	}
    }

    public void testSetFlatFalse() {
	String targetName = "set-format-xml";
	Operation operation = (Operation) getFirstStepFromTarget(targetName);
	assertTrue("Operation attribute format should have been 'xml', but was: " + operation.getFormat(),
		operation.getFormat().equalsIgnoreCase("xml"));
    }

    public void testResolveOperationTypes() {
	assertOperationType("Should have been a NONE operation", "test-type-none", DatabaseOperation.NONE);
	assertOperationType("Should have been an DELETE_ALL operation", "test-type-delete-all",
		DatabaseOperation.DELETE_ALL);
	assertOperationType("Should have been an INSERT operation", "test-type-insert", DatabaseOperation.INSERT);
	assertOperationType("Should have been an UPDATE operation", "test-type-update", DatabaseOperation.UPDATE);
	assertOperationType("Should have been an REFRESH operation", "test-type-refresh", DatabaseOperation.REFRESH);
	assertOperationType("Should have been an CLEAN_INSERT operation", "test-type-clean-insert",
		DatabaseOperation.CLEAN_INSERT);
	assertOperationType("Should have been an CLEAN_INSERT operation", "test-type-clean-insert-composite",
		DatabaseOperation.CLEAN_INSERT);
	assertOperationType("Should have been an CLEAN_INSERT operation", "test-type-clean-insert-composite-combine",
		DatabaseOperation.CLEAN_INSERT);
	assertOperationType("Should have been an DELETE operation", "test-type-delete", DatabaseOperation.DELETE);
	assertOperationType("Should have been an MSSQL_INSERT operation", "test-type-mssql-insert",
		InsertIdentityOperation.INSERT);
	assertOperationType("Should have been an MSSQL_REFRESH operation", "test-type-mssql-refresh",
		InsertIdentityOperation.REFRESH);
	assertOperationType("Should have been an MSSQL_CLEAN_INSERT operation", "test-type-mssql-clean-insert",
		InsertIdentityOperation.CLEAN_INSERT);
    }

    public void testInvalidCompositeOperationSrc() {
	expectBuildException("invalid-composite-operation-src",
		"Should have objected to nested operation src attribute " + "being set.");
    }

    public void testInvalidCompositeOperationFlat() {
	expectBuildException("invalid-composite-operation-format-flat",
		"Should have objected to nested operation format attribute " + "being set.");
    }

    public void testExportFull() {
	String targetName = "test-export-full";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertTrue("Should have been a flat format, " + "but was: " + export.getFormat(),
		export.getFormat().equalsIgnoreCase("flat"));
	List tables = export.getTables();
	assertTrue("Should have been an empty table list " + "(indicating a full dataset), but was: " + tables,
		tables.size() == 0);
    }

    public void testExportPartial() {
	String targetName = "test-export-partial";
	Export export = (Export) getFirstStepFromTarget(targetName);
	List tables = export.getTables();
	assertEquals("table count", 2, tables.size());
	Table testTable = (Table) tables.get(0);
	Table pkTable = (Table) tables.get(1);
	assertTrue("Should have been been TABLE TEST_TABLE, but was: " + testTable.getName(),
		testTable.getName().equals("TEST_TABLE"));
	assertTrue("Should have been been TABLE PK_TABLE, but was: " + pkTable.getName(),
		pkTable.getName().equals("PK_TABLE"));
    }

    public void testExportWithForwardOnlyResultSetTable() throws SQLException, DatabaseUnitException {
	String targetName = "test-export-forward-only-result-set-table-via-config";

	// Test if the correct result set table factory is set according to dbconfig
	Export export = (Export) getFirstStepFromTarget(targetName);
	DbUnitTask task = getFirstTargetTask(targetName);
	IDatabaseConnection connection = task.createConnection();
	IDataSet dataSetToBeExported = export.getExportDataSet(connection);
	assertEquals("org.dbunit.database.ForwardOnlyResultSetTableFactory", connection.getConfig()
		.getProperty(DatabaseConfig.PROPERTY_RESULTSET_TABLE_FACTORY).getClass().getName());
    }

    public void testExportFlat() {
	String targetName = "test-export-format-flat";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("format", "flat", export.getFormat());
    }

    public void testExportFlatWithDocytpe() {
	String targetName = "test-export-format-flat-with-doctype";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("format", "flat", export.getFormat());
	assertEquals("doctype", "dataset.dtd", export.getDoctype());
    }

    public void testExportFlatWithEncoding() {
	String targetName = "test-export-format-flat-with-encoding";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("format", "flat", export.getFormat());
	assertEquals("encoding", "ISO-8859-1", export.getEncoding());
    }

    public void testExportXml() {
	String targetName = "test-export-format-xml";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertTrue("Should have been an xml format, " + "but was: " + export.getFormat(),
		export.getFormat().equalsIgnoreCase("xml"));
    }

    public void testExportCsv() {
	String targetName = "test-export-format-csv";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertTrue("Should have been a csv format, " + "but was: " + export.getFormat(),
		export.getFormat().equalsIgnoreCase("csv"));
    }

    public void testExportDtd() {
	String targetName = "test-export-format-dtd";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertTrue("Should have been a dtd format, " + "but was: " + export.getFormat(),
		export.getFormat().equalsIgnoreCase("dtd"));
    }

    public void testInvalidExportFormat() {
	expectBuildException("invalid-export-format", "Should have objected to invalid format attribute.");
    }

    public void testExportXmlOrdered() throws Exception {
	String targetName = "test-export-format-xml-ordered";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("Should be ordered", true, export.isOrdered());
	assertTrue("Should have been an xml format, " + "but was: " + export.getFormat(),
		export.getFormat().equalsIgnoreCase("xml"));

	// Test if the correct dataset is created for ordered export
	DbUnitTask task = getFirstTargetTask(targetName);
	IDatabaseConnection connection = task.createConnection();
	IDataSet dataSetToBeExported = export.getExportDataSet(connection);
	// Ordered export should use the filtered dataset
	assertEquals(dataSetToBeExported.getClass(), FilteredDataSet.class);
    }

    public void testExportQuery() {
	String targetName = "test-export-query";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("format", "flat", export.getFormat());

	List queries = export.getTables();
	assertEquals("query count", 2, getQueryCount(queries));

	Query testTable = (Query) queries.get(0);
	assertEquals("name", "TEST_TABLE", testTable.getName());
	assertEquals("sql", "SELECT * FROM TEST_TABLE ORDER BY column0 DESC", testTable.getSql());

	Query pkTable = (Query) queries.get(1);
	assertEquals("name", "PK_TABLE", pkTable.getName());
	assertEquals("sql", "SELECT * FROM PK_TABLE", pkTable.getSql());
    }

    public void testExportWithQuerySet() {
	String targetName = "test-export-with-queryset";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("format", "csv", export.getFormat());

	List queries = export.getTables();

	assertEquals("query count", 1, getQueryCount(queries));
	assertEquals("table count", 1, getTableCount(queries));
	assertEquals("queryset count", 2, getQuerySetCount(queries));

	Query secondTable = (Query) queries.get(0);
	assertEquals("name", "SECOND_TABLE", secondTable.getName());
	assertEquals("sql", "SELECT * FROM SECOND_TABLE", secondTable.getSql());

	QuerySet queryset1 = (QuerySet) queries.get(1);

	Query testTable = (Query) queryset1.getQueries().get(0);

	assertEquals("name", "TEST_TABLE", testTable.getName());

	QuerySet queryset2 = (QuerySet) queries.get(2);

	Query pkTable = (Query) queryset2.getQueries().get(0);
	Query testTable2 = (Query) queryset2.getQueries().get(1);

	assertEquals("name", "PK_TABLE", pkTable.getName());
	assertEquals("name", "TEST_TABLE", testTable2.getName());

	Table emptyTable = (Table) queries.get(3);

	assertEquals("name", "EMPTY_TABLE", emptyTable.getName());
    }

    public void testWithBadQuerySet() {
	try {
	    expectBuildException("invalid-queryset",
		    "Cannot specify 'id' and 'refid' attributes together in queryset.");
	} catch (AssertionError exc) {
	    // ignoring for now. New version of ant swallows BuildException on id attribute
	    // set.
	}
    }

    public void testWithReferenceQuerySet() {
	String targetName = "test-queryset-reference";

	Export export = (Export) getFirstStepFromTarget(targetName);

	List tables = export.getTables();

	assertEquals("total count", 1, tables.size());

	QuerySet queryset = (QuerySet) tables.get(0);
	Query testTable = (Query) queryset.getQueries().get(0);
	Query secondTable = (Query) queryset.getQueries().get(1);

	assertEquals("name", "TEST_TABLE", testTable.getName());
	assertEquals("sql", "SELECT * FROM TEST_TABLE WHERE COLUMN0 = 'row0 col0'", testTable.getSql());

	assertEquals("name", "SECOND_TABLE", secondTable.getName());
	assertEquals("sql", "SELECT B.* FROM TEST_TABLE A, SECOND_TABLE B "
		+ "WHERE A.COLUMN0 = 'row0 col0' AND B.COLUMN0 = A.COLUMN0", secondTable.getSql());

    }

    public void testExportQueryMixed() {
	String targetName = "test-export-query-mixed";
	Export export = (Export) getFirstStepFromTarget(targetName);
	assertEquals("format", "flat", export.getFormat());

	List tables = export.getTables();
	assertEquals("total count", 2, tables.size());
	assertEquals("table count", 1, getTableCount(tables));
	assertEquals("query count", 1, getQueryCount(tables));

	Table testTable = (Table) tables.get(0);
	assertEquals("name", "TEST_TABLE", testTable.getName());

	Query pkTable = (Query) tables.get(1);
	assertEquals("name", "PK_TABLE", pkTable.getName());
    }

    /**
     * Tests the exception that is thrown when the compare fails because the source
     * format was different from the previous "export" task's write format.
     */
    public void testExportAndCompareFormatMismatch() {
	String targetName = "test-export-and-compare-format-mismatch";

	try {
	    getFirstTargetTask(targetName);
	    fail("Should not be able to invoke ant task where the expected table was not found because it was tried to read in the wrong format.");
	} catch (BuildException expected) {
	    Throwable cause = expected.getCause();
	    assertTrue(cause instanceof DatabaseUnitException);
	    DatabaseUnitException dbUnitException = (DatabaseUnitException) cause;
	    String filename = new File(outputDir, "antExportDataSet.xml").toString();
	    String expectedMsg = "Did not find table in source file '" + filename + "' using format 'xml'";
	    assertEquals(expectedMsg, dbUnitException.getMessage());
	    assertTrue(dbUnitException.getCause() instanceof NoSuchTableException);
	    NoSuchTableException nstException = (NoSuchTableException) dbUnitException.getCause();
	    assertEquals("TEST_TABLE", nstException.getMessage());
	}
    }

    public void testDataTypeFactory() throws Exception {
	String targetName = "test-datatypefactory";
	DbUnitTask task = getFirstTargetTask(targetName);

	IDatabaseConnection connection = task.createConnection();
	IDataTypeFactory factory = (IDataTypeFactory) connection.getConfig()
		.getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY);

	Class expectedClass = OracleDataTypeFactory.class;
	assertEquals("factory", expectedClass, factory.getClass());
    }

    public void testEscapePattern() throws Exception {
	String targetName = "test-escapepattern";
	DbUnitTask task = getFirstTargetTask(targetName);

	IDatabaseConnection connection = task.createConnection();
	String actualPattern = (String) connection.getConfig().getProperty(DatabaseConfig.PROPERTY_ESCAPE_PATTERN);

	String expectedPattern = "[?]";
	assertEquals("factory", expectedPattern, actualPattern);
    }

    public void testDataTypeFactoryViaGenericConfig() throws Exception {
	String targetName = "test-datatypefactory-via-generic-config";
	DbUnitTask task = getFirstTargetTask(targetName);

	IDatabaseConnection connection = task.createConnection();

	DatabaseConfig config = connection.getConfig();

	IDataTypeFactory factory = (IDataTypeFactory) config.getProperty(DatabaseConfig.PROPERTY_DATATYPE_FACTORY);
	Class expectedClass = OracleDataTypeFactory.class;
	assertEquals("factory", expectedClass, factory.getClass());

	String[] actualTableType = (String[]) config.getProperty(DatabaseConfig.PROPERTY_TABLE_TYPE);
	ArrayAssert.assertEquals("tableType", new String[] { "TABLE", "SYNONYM" }, actualTableType);
	assertTrue("batched statements feature should be true",
		connection.getConfig().getFeature(DatabaseConfig.FEATURE_BATCHED_STATEMENTS));
	assertTrue("qualified tablenames feature should be true",
		connection.getConfig().getFeature(DatabaseConfig.FEATURE_CASE_SENSITIVE_TABLE_NAMES));
    }

    public void testClasspath() throws Exception {
	String targetName = "test-classpath";

	try {
	    executeTarget(targetName);
	    fail("Should not be able to connect with invalid url!");
	} catch (BuildException e) {
	    // Verify exception type
	    assertTrue("nested exxception type", e.getException() instanceof SQLException);
	}

    }

    public void testDriverNotInClasspath() throws Exception {
	String targetName = "test-drivernotinclasspath";

	try {
	    executeTarget(targetName);
	    fail("Should not have found driver!");
	} catch (BuildException e) {
	    // Verify exception type
	    assertEquals("nested exception type", ClassNotFoundException.class, e.getException().getClass());
	}
    }

    public void testReplaceOperation() throws Exception {
	String targetName = "test-replace";
	final IDatabaseTester dbTest = DatabaseEnvironmentLoader.getInstance(null).getDatabaseTester();
	executeTarget(targetName);
	final IDataSet ds = dbTest.getConnection().createDataSet();
	final ITable table = ds.getTable("PK_TABLE");
	assertNull(table.getValue(0, "NORMAL0"));
	assertEquals("row 1", table.getValue(1, "NORMAL0"));
    }

    public void testOrderedOperation() throws Exception {
	String targetName = "test-ordered";
	final IDatabaseTester dbTest = DatabaseEnvironmentLoader.getInstance(null).getDatabaseTester();
	executeTarget(targetName);
	final IDataSet ds = dbTest.getConnection().createDataSet();
	final ITable table = ds.getTable("PK_TABLE");
	assertEquals("row 0", table.getValue(0, "NORMAL0"));
	assertEquals("row 1", table.getValue(1, "NORMAL0"));
    }

    public void testReplaceOrderedOperation() throws Exception {
	String targetName = "test-replace-ordered";
	final IDatabaseTester dbTest = DatabaseEnvironmentLoader.getInstance(null).getDatabaseTester();
	executeTarget(targetName);
	final IDataSet ds = dbTest.getConnection().createDataSet();
	final ITable table = ds.getTable("PK_TABLE");
	assertNull(table.getValue(0, "NORMAL0"));
	assertEquals("row 1", table.getValue(1, "NORMAL0"));
    }

    protected void assertOperationType(String failMessage, String targetName, DatabaseOperation expected) {
	Operation oper = (Operation) getFirstStepFromTarget(targetName);
	DatabaseOperation dbOper = oper.getDbOperation();
	assertTrue(failMessage + ", but was: " + dbOper, expected.equals(dbOper));
    }

    protected int getQueryCount(List tables) {
	int count = 0;
	for (Iterator it = tables.iterator(); it.hasNext();) {
	    if (it.next() instanceof Query) {
		count++;
	    }
	}

	return count;
    }

    protected int getTableCount(List tables) {
	int count = 0;
	for (Iterator it = tables.iterator(); it.hasNext();) {
	    if (it.next() instanceof Table) {
		count++;
	    }
	}

	return count;
    }

    protected int getQuerySetCount(List tables) {
	int count = 0;
	for (Iterator it = tables.iterator(); it.hasNext();) {
	    if (it.next() instanceof QuerySet) {
		count++;
	    }
	}

	return count;
    }

    protected DbUnitTaskStep getFirstStepFromTarget(String targetName) {
	return getStepFromTarget(targetName, 0);
    }

    protected DbUnitTaskStep getStepFromTarget(String targetName, int index) {
	DbUnitTask task = getFirstTargetTask(targetName);
	List steps = task.getSteps();
	if (steps == null || steps.size() == 0) {
	    fail("Can't get a dbunit <step> from the target: " + targetName + ". No steps available.");
	}
	return (DbUnitTaskStep) steps.get(index);
    }

    private DbUnitTask getFirstTargetTask(String targetName) {
	Hashtable targets = project.getTargets();
	executeTarget(targetName);
	Target target = (Target) targets.get(targetName);

	Task[] tasks = target.getTasks();
	for (int i = 0; i < tasks.length; i++) {
	    Object task = tasks[i];
	    if (task instanceof UnknownElement) {
		((UnknownElement) task).maybeConfigure(); // alternative to this is setting id on dbunit task. then ant
							  // will not clean realThing
		task = ((UnknownElement) task).getRealThing();
	    }
	    if (task instanceof DbUnitTask) {
		return (DbUnitTask) task;
	    }
	}

	return null;
    }

    public static Test suite() {
	TestSuite suite = new TestSuite(classUnderTest);
	return suite;
    }

    public static void main(String args[]) {
	if (args.length > 0 && args[0].equals("-gui")) {
	    System.err.println("JUnit Swing-GUI is no longer supported.");
	    System.err.println("Starting textversion.");
	}

	junit.textui.TestRunner.run(suite());
    }

    protected Project project;

    private StringBuffer logBuffer;
    private StringBuffer fullLogBuffer;
    private StringBuffer outBuffer;
    private StringBuffer errBuffer;
    private BuildException buildException;

    /**
     * Automatically calls the target called "tearDown" from the build file tested
     * if it exits.
     *
     * This allows to use Ant tasks directly in the build file to clean up after
     * each test. Note that no "setUp" target is automatically called, since it's
     * trivial to have a test target depend on it.
     *
     * @throws Exception this implementation doesn't throw any exception but we've
     *                   added it to the signature so that subclasses can throw
     *                   whatever they need.
     */
    protected void super_tearDown() throws Exception {
	if (project == null) {
	    /*
	     * Maybe the BuildFileTest was subclassed and there is no initialized project.
	     * So we could avoid getting a NPE. If there is an initialized project
	     * getTargets() does not return null as it is initialized by an empty HashSet.
	     */
	    return;
	}
	final String tearDown = "tearDown";
	if (project.getTargets().containsKey(tearDown)) {
	    project.executeTarget(tearDown);
	}
    }

    /**
     * run a target, expect for any build exception
     *
     * @param target target to run
     * @param cause  information string to reader of report
     */
    public void expectBuildException(String target, String cause) {
	expectSpecificBuildException(target, cause, null);
    }

    /**
     * Assert that only the given message has been logged with a priority &lt;= INFO
     * when running the given target.
     *
     * @param target String
     * @param log    String
     */
    public void expectLog(String target, String log) {
	executeTarget(target);
	String realLog = getLog();
	assertEquals(log, realLog);
    }

    /**
     * Assert that the given substring is in the log messages.
     *
     * @param substring String
     */
    public void assertLogContaining(String substring) {
	String realLog = getLog();
	assertTrue("expecting log to contain \"" + substring + "\" log was \"" + realLog + "\"",
		realLog.contains(substring));
    }

    /**
     * Assert that the given substring is not in the log messages.
     *
     * @param substring String
     */
    public void assertLogNotContaining(String substring) {
	String realLog = getLog();
	assertFalse("didn't expect log to contain \"" + substring + "\" log was \"" + realLog + "\"",
		realLog.contains(substring));
    }

    /**
     * Assert that the given substring is in the output messages.
     *
     * @param substring String
     * @since Ant1.7
     */
    public void assertOutputContaining(String substring) {
	assertOutputContaining(null, substring);
    }

    /**
     * Assert that the given substring is in the output messages.
     *
     * @param message   Print this message if the test fails. Defaults to a
     *                  meaningful text if <code>null</code> is passed.
     * @param substring String
     * @since Ant1.7
     */
    public void assertOutputContaining(String message, String substring) {
	String realOutput = getOutput();
	String realMessage = (message != null) ? message
		: "expecting output to contain \"" + substring + "\" output was \"" + realOutput + "\"";
	assertTrue(realMessage, realOutput.contains(substring));
    }

    /**
     * Assert that the given substring is not in the output messages.
     *
     * @param message   Print this message if the test fails. Defaults to a
     *                  meaningful text if <code>null</code> is passed.
     * @param substring String
     * @since Ant1.7
     */
    public void assertOutputNotContaining(String message, String substring) {
	String realOutput = getOutput();
	String realMessage = (message != null) ? message
		: "expecting output to not contain \"" + substring + "\" output was \"" + realOutput + "\"";
	assertFalse(realMessage, realOutput.contains(substring));
    }

    /**
     * Assert that the given message has been logged with a priority &lt;= INFO when
     * running the given target.
     *
     * @param target String
     * @param log    String
     */
    public void expectLogContaining(String target, String log) {
	executeTarget(target);
	assertLogContaining(log);
    }

    /**
     * Assert that the given message has not been logged with a priority &lt;= INFO
     * when running the given target.
     *
     * @param target String
     * @param log    String
     */
    public void expectLogNotContaining(String target, String log) {
	executeTarget(target);
	assertLogNotContaining(log);
    }

    /**
     * Gets the log the BuildFileTest object. Only valid if configureProject() has
     * been called.
     *
     * @pre logBuffer!=null
     * @return The log value
     */
    public String getLog() {
	return logBuffer.toString();
    }

    /**
     * Assert that the given message has been logged with a priority &gt;= VERBOSE
     * when running the given target.
     *
     * @param target String
     * @param log    String
     */
    public void expectDebuglog(String target, String log) {
	executeTarget(target);
	String realLog = getFullLog();
	assertEquals(log, realLog);
    }

    /**
     * Assert that the given substring is in the log messages.
     *
     * @param substring String
     */
    public void assertDebuglogContaining(String substring) {
	String realLog = getFullLog();
	assertTrue("expecting debug log to contain \"" + substring + "\" log was \"" + realLog + "\"",
		realLog.contains(substring));
    }

    /**
     * Gets the log the BuildFileTest object.
     *
     * Only valid if configureProject() has been called.
     *
     * @pre fullLogBuffer!=null
     * @return The log value
     */
    public String getFullLog() {
	return fullLogBuffer.toString();
    }

    /**
     * execute the target, verify output matches expectations
     *
     * @param target target to execute
     * @param output output to look for
     */
    public void expectOutput(String target, String output) {
	executeTarget(target);
	String realOutput = getOutput();
	assertEquals(output, realOutput.trim());
    }

    /**
     * Executes the target, verify output matches expectations and that we got the
     * named error at the end
     *
     * @param target target to execute
     * @param output output to look for
     * @param error  Description of Parameter
     */
    public void expectOutputAndError(String target, String output, String error) {
	executeTarget(target);
	String realOutput = getOutput();
	assertEquals(output, realOutput);
	String realError = getError();
	assertEquals(error, realError);
    }

    public String getOutput() {
	return cleanBuffer(outBuffer);
    }

    public String getError() {
	return cleanBuffer(errBuffer);
    }

    public BuildException getBuildException() {
	return buildException;
    }

    private String cleanBuffer(StringBuffer buffer) {
	StringBuilder cleanedBuffer = new StringBuilder();
	for (int i = 0; i < buffer.length(); i++) {
	    char ch = buffer.charAt(i);
	    if (ch != '\r') {
		cleanedBuffer.append(ch);
	    }
	}
	return cleanedBuffer.toString();
    }

    /**
     * Sets up to run the named project
     *
     * @param filename name of project file to run
     */
    public void configureProject(String filename) throws BuildException {
	configureProject(filename, Project.MSG_DEBUG);
    }

    /**
     * Sets up to run the named project
     *
     * @param filename name of project file to run
     * @param logLevel int
     */
    public void configureProject(String filename, int logLevel) throws BuildException {
	logBuffer = new StringBuffer();
	fullLogBuffer = new StringBuffer();
	project = new Project();
	if (Boolean.getBoolean(MagicTestNames.TEST_BASEDIR_IGNORE)) {
	    System.clearProperty(MagicNames.PROJECT_BASEDIR);
	}
	project.init();
	File antFile = new File(System.getProperty(MagicTestNames.TEST_ROOT_DIRECTORY), filename);
	project.setUserProperty(MagicNames.ANT_FILE, antFile.getAbsolutePath());
	// set two new properties to allow to build unique names when running
	// multithreaded tests
	project.setProperty(MagicTestNames.TEST_PROCESS_ID, ProcessUtil.getProcessId("<Process>"));
	project.setProperty(MagicTestNames.TEST_THREAD_NAME, Thread.currentThread().getName());
	project.addBuildListener(new AntTestListener(logLevel));
	ProjectHelper.configureProject(project, antFile);
    }

    /**
     * Executes a target we have set up
     *
     * @pre configureProject has been called
     * @param targetName target to run
     */
    public void executeTarget(String targetName) {
	PrintStream sysOut = System.out;
	PrintStream sysErr = System.err;
	try {
	    sysOut.flush();
	    sysErr.flush();
	    outBuffer = new StringBuffer();
	    PrintStream out = new PrintStream(new AntOutputStream(outBuffer));
	    System.setOut(out);
	    errBuffer = new StringBuffer();
	    PrintStream err = new PrintStream(new AntOutputStream(errBuffer));
	    System.setErr(err);
	    logBuffer = new StringBuffer();
	    fullLogBuffer = new StringBuffer();
	    buildException = null;
	    project.executeTarget(targetName);
	} finally {
	    System.setOut(sysOut);
	    System.setErr(sysErr);
	}

    }

    /**
     * Get the project which has been configured for a test.
     *
     * @return the Project instance for this test.
     */
    public Project getProject() {
	return project;
    }

    /**
     * Gets the directory of the project.
     *
     * @return the base dir of the project
     */
    public File getProjectDir() {
	return project.getBaseDir();
    }

    /**
     * get location of temporary directory pointed to by property "output"
     * 
     * @return location of temporary directory pointed to by property "output"
     * @since Ant 1.9.4
     */
    public File getOutputDir() {
	return new File(project.getProperty("output"));
    }

    /**
     * Runs a target, wait for a build exception.
     *
     * @param target target to run
     * @param cause  information string to reader of report
     * @param msg    the message value of the build exception we are waiting for set
     *               to null for any build exception to be valid
     */
    public void expectSpecificBuildException(String target, String cause, String msg) {
	try {
	    executeTarget(target);
	} catch (BuildException ex) {
	    buildException = ex;
	    assertTrue("Should throw BuildException because '" + cause + "' with message '" + msg
		    + "' (actual message '" + ex.getMessage() + "' instead)",
		    msg == null || ex.getMessage().equals(msg));
	    return;
	}
	fail("Should throw BuildException because: " + cause);
    }

    /**
     * run a target, expect an exception string containing the substring we look for
     * (case sensitive match)
     *
     * @param target   target to run
     * @param cause    information string to reader of report
     * @param contains substring of the build exception to look for
     */
    public void expectBuildExceptionContaining(String target, String cause, String contains) {
	try {
	    executeTarget(target);
	} catch (BuildException ex) {
	    buildException = ex;
	    assertTrue(
		    "Should throw BuildException because '" + cause + "' with message containing '" + contains
			    + "' (actual message '" + ex.getMessage() + "' instead)",
		    null == contains || ex.getMessage().contains(contains));
	    return;
	}
	fail("Should throw BuildException because: " + cause);
    }

    /**
     * call a target, verify property is as expected
     *
     * @param target   build file target
     * @param property property name
     * @param value    expected value
     */
    public void expectPropertySet(String target, String property, String value) {
	executeTarget(target);
	assertPropertyEquals(property, value);
    }

    /**
     * assert that a property equals a value; comparison is case sensitive.
     *
     * @param property property name
     * @param value    expected value
     */
    public void assertPropertyEquals(String property, String value) {
	String result = project.getProperty(property);
	assertEquals("property " + property, value, result);
    }

    /**
     * assert that a property equals "true".
     *
     * @param property property name
     */
    public void assertPropertySet(String property) {
	assertPropertyEquals(property, "true");
    }

    /**
     * assert that a property is null.
     *
     * @param property property name
     */
    public void assertPropertyUnset(String property) {
	String result = project.getProperty(property);
	assertNull("Expected property " + property + " to be unset, but it is set to the value: " + result, result);
    }

    /**
     * call a target, verify named property is "true".
     *
     * @param target   build file target
     * @param property property name
     */
    public void expectPropertySet(String target, String property) {
	expectPropertySet(target, property, "true");
    }

    /**
     * Call a target, verify property is null.
     *
     * @param target   build file target
     * @param property property name
     */
    public void expectPropertyUnset(String target, String property) {
	expectPropertySet(target, property, null);
    }

    /**
     * Retrieve a resource from the caller classloader to avoid assuming a vm
     * working directory. The resource path must be relative to the package name or
     * absolute from the root path.
     *
     * @param resource the resource to retrieve its url.
     * @return URL ditto
     */
    public URL getResource(String resource) {
	URL url = getClass().getResource(resource);
	assertNotNull("Could not find resource :" + resource, url);
	return url;
    }

    /**
     * an output stream which saves stuff to our buffer.
     */
    protected static class AntOutputStream extends OutputStream {
	private StringBuffer buffer;

	public AntOutputStream(StringBuffer buffer) {
	    this.buffer = buffer;
	}

	@Override
	public void write(int b) {
	    buffer.append((char) b);
	}
    }

    /**
     * Our own personal build listener.
     */
    private class AntTestListener implements BuildListener {
	private int logLevel;

	/**
	 * Constructs a test listener which will ignore log events above the given
	 * level.
	 */
	public AntTestListener(int logLevel) {
	    this.logLevel = logLevel;
	}

	/**
	 * Fired before any targets are started.
	 */
	@Override
	public void buildStarted(BuildEvent event) {
	}

	/**
	 * Fired after the last target has finished. This event will still be thrown if
	 * an error occurred during the build.
	 *
	 * @see BuildEvent#getException()
	 */
	@Override
	public void buildFinished(BuildEvent event) {
	}

	/**
	 * Fired when a target is started.
	 *
	 * @see BuildEvent#getTarget()
	 */
	@Override
	public void targetStarted(BuildEvent event) {
	    // System.out.println("targetStarted " + event.getTarget().getName());
	}

	/**
	 * Fired when a target has finished. This event will still be thrown if an error
	 * occurred during the build.
	 *
	 * @see BuildEvent#getException()
	 */
	@Override
	public void targetFinished(BuildEvent event) {
	    // System.out.println("targetFinished " + event.getTarget().getName());
	}

	/**
	 * Fired when a task is started.
	 *
	 * @see BuildEvent#getTask()
	 */
	@Override
	public void taskStarted(BuildEvent event) {
	    // System.out.println("taskStarted " + event.getTask().getTaskName());
	}

	/**
	 * Fired when a task has finished. This event will still be throw if an error
	 * occurred during the build.
	 *
	 * @see BuildEvent#getException()
	 */
	@Override
	public void taskFinished(BuildEvent event) {
	    // System.out.println("taskFinished " + event.getTask().getTaskName());
	}

	/**
	 * Fired whenever a message is logged.
	 *
	 * @see BuildEvent#getMessage()
	 * @see BuildEvent#getPriority()
	 */
	@Override
	public void messageLogged(BuildEvent event) {
	    if (event.getPriority() > logLevel) {
		// ignore event
		return;
	    }

	    if (event.getPriority() == Project.MSG_INFO || event.getPriority() == Project.MSG_WARN
		    || event.getPriority() == Project.MSG_ERR) {
		logBuffer.append(event.getMessage());
	    }
	    fullLogBuffer.append(event.getMessage());
	}
    }

}
