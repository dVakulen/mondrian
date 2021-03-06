/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2012-2013 Pentaho
// All Rights Reserved.
*/
package mondrian.test;

import mondrian.olap.MondrianException;
import mondrian.olap.Util;

import org.olap4j.*;

import java.sql.*;

/**
 * Unit test for hanger dimensions.
 */
public class HangerDimensionTest extends FoodMartTestCase {
    /** Unit test for a simple hanger dimension with values true and false. */
    public void testHangerDimension() {
      TestContext testContext = TestContext.instance().createSubstitutingCube(
          "Sales",
          "<Dimension name='Boolean' hanger='true'>\n"
          + "  <Hierarchy hasAll=\"true\">\n"
          + "    <Level name=\"Boolean\"/>\n"
          + "  </Hierarchy>\n"
          + "</Dimension>",
          "<CalculatedMember name='False' hierarchy='[Boolean]' formula='[Marital Status]'/>\n"
          + "<CalculatedMember name='True' hierarchy='[Boolean]' formula='[Marital Status]'/>\n");
      testContext.assertQueryReturns(
          "with member [Measures].[Store Sales2] as\n"
          + "   Iif([Boolean].CurrentMember is [Boolean].[True],\n"
          + "       ([Boolean].[All Booleans], [Measures].[Store Sales]),"
          + "       ([Boolean].[All Booleans], [Measures].[Store Sales]) - ([Boolean].[All Booleans], [Measures].[Store Cost]))\n"
          + "select [Measures].[Store Sales2] on columns,\n"
          + " [Boolean].AllMembers * [Gender].Children on rows\n"
          + "from [Sales]",
          "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Measures].[Store Sales2]}\n"
          + "Axis #2:\n"
          + "{[Boolean].[All Booleans], [Gender].[F]}\n"
          + "{[Boolean].[All Booleans], [Gender].[M]}\n"
          + "{[Boolean].[False], [Gender].[F]}\n"
          + "{[Boolean].[False], [Gender].[M]}\n"
          + "{[Boolean].[True], [Gender].[F]}\n"
          + "{[Boolean].[True], [Gender].[M]}\n"
          + "Row #0: 168,448.73\n"
          + "Row #1: 171,162.17\n"
          + "Row #2: 168,448.73\n"
          + "Row #3: 171,162.17\n"
          + "Row #4: 280,226.21\n"
          + "Row #5: 285,011.92\n");
    }

    public void testHangerWithDescendants() {

      TestContext testContext = TestContext.instance().createSubstitutingCube(
          "Sales",
          "<Dimension name='Boolean' hanger='true'>\n"
          + "  <Hierarchy hasAll=\"true\">\n"
          + "    <Table name=\"store\"/>\n"
          + "    <Level name=\"Boolean\" column=\"store_name\"/>\n"
          + "  </Hierarchy>\n"
          + "</Dimension>",
          "<CalculatedMember name='False' hierarchy='[Boolean]' formula='[Marital Status]'/>\n"
          + "<CalculatedMember name='True' hierarchy='[Boolean]' formula='[Marital Status]'/>\n");

      String mdx =
          "with set [All Measures Fullset] as '{[Measures].[Unit Sales]}'\n"
        + "member [Measures].[(Axis Members Count)] as 'Count(Descendants([Boolean].[All Booleans], 1, SELF_AND_BEFORE))', SOLVE_ORDER = 1000\n"
        + "select {[Measures].[(Axis Members Count)]} ON COLUMNS\n"
        + "from [Sales]";

      testContext.assertQueryReturns( mdx,
          "Axis #0:\n"
          + "{}\n"
          + "Axis #1:\n"
          + "{[Measures].[(Axis Members Count)]}\n"
          + "Row #0: 26\n");
    }

    /**
     * NOTE: In Mondrian 3, hanger dimensions must either be based on a table
     * or have an all member.
     * 
     * This is due to the way we resolve calculated members, we cannot set the
     * default member due to how Query.resolve() behaves in 3.x 
     * (requiring a RootEvaluator)
     * 
     * Unit test that if a hierarchy has no real members, only calculated
     * members, then the default member is the first calculated member. 
     * 
     */
    public void _testHangerDimensionImplicitCalculatedDefaultMember() {
      TestContext testContext = TestContext.instance().createSubstitutingCube(
          "Sales",
          "<Dimension name='Boolean' hanger='true'>\n"
          + "  <Hierarchy hasAll=\"false\">\n"
          + "    <Level name=\"Boolean\"/>\n"
          + "  </Hierarchy>\n"
          + "</Dimension>",
          "<CalculatedMember name='False' hierarchy='[Boolean]' formula='[Marital Status]'/>\n"
          + "<CalculatedMember name='True' hierarchy='[Boolean]' formula='[Marital Status]'/>\n");
       testContext.assertAxisReturns(
          "[Boolean]",
          "[Boolean].[False]");
    }

    /** Tests that it is an error if an attribute has no members.
     * (No all member, no real members, no calculated members.) */
    public void testHangerDimensionEmptyIsError() {
        TestContext testContext = TestContext.instance().createSubstitutingCube(
            "Sales",
            "<Dimension name='Boolean' hanger='true'>\n"
            + "  <Hierarchy hasAll=\"false\">\n"
            + "    <Level name=\"Boolean\"/>\n"
            + "  </Hierarchy>\n"
            + "</Dimension>");
        try {
            testContext.getConnection();
            fail();
        } catch (MondrianException e) {
            assertTrue("Missing (has no members): " + e.getCause().getCause().getMessage(), e.getCause().getCause().getMessage().indexOf( "Mondrian Error:Hierarchy '[Boolean]' is invalid (has no members)") >= 0);
        }
    }

    /** Tests that it is an error if an attribute in a hanger dimension has a
     * keyColumn specified. (All other mappings to columns, e.g. nameColumn
     * or included Key element, are illegal too.) */
    public void testHangerDimensionKeyColumnNotAllowed() {
      TestContext testContext = TestContext.instance().createSubstitutingCube(
                "Sales",
                "<Dimension name='Boolean' hanger='true' key='Boolean'>\n"
                + "  <Attributes>\n"
                + "    <Attribute name='Boolean' keyColumn='xxx'/>\n"
                + "  </Attributes>\n"
                + "</Dimension>");
                /*
                 * 
                 * TODO: Figure out the appropriate scenario here
                 * 
            .assertErrorList().containsError(
                "Attribute 'Boolean' in hanger dimension must not map to column \\(in Attribute 'Boolean'\\) \\(at ${pos}\\)",
                "<Attribute name='Boolean' keyColumn='xxx'/>");
                */
    }

    /** Tests drill-through of a query involving a hanger dimension. */
    public void testHangerDimensionDrillThrough() throws SQLException {
        OlapConnection connection = null;
        OlapStatement statement = null;
        CellSet cellSet = null;
        ResultSet resultSet = null;
        try {
          
            TestContext testContext = TestContext.instance().createSubstitutingCube(          
                "Sales",
                "<Dimension name='Boolean' hanger='true'>\n"
                + "  <Hierarchy hasAll=\"true\">\n"
                + "    <Level name=\"Boolean\"/>\n"
                + "  </Hierarchy>\n"
                + "</Dimension>",
                "<CalculatedMember name='False' hierarchy='[Boolean]' formula='[Marital Status]'/>\n"
                + "<CalculatedMember name='True' hierarchy='[Boolean]' formula='[Marital Status]'/>\n");
            
            connection = testContext.getOlap4jConnection();
            statement = connection.createStatement();
            cellSet =
                statement.executeOlapQuery(
                    "select [Gender].Members on 0,\n"
                    + "[Boolean].Members on 1\n"
                    + "from [Sales]");
            resultSet = cellSet.getCell(0).drillThrough();
            int n = 0;
            while (resultSet.next()) {
                ++n;
            }
            assertEquals(86837, n);
        } finally {
            Util.close(resultSet, null, null);
            Util.close(cellSet, statement, connection);
        }
    }
}

// End HangerDimensionTest.java
