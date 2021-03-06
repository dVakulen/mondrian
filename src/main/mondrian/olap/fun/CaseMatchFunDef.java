/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (c) 2002-2014 Pentaho Corporation..  All rights reserved.
*/
package mondrian.olap.fun;

import mondrian.calc.Calc;
import mondrian.calc.ExpCompiler;
import mondrian.calc.impl.ConstantCalc;
import mondrian.calc.impl.GenericCalc;
import mondrian.mdx.ResolvedFunCall;
import mondrian.olap.*;
import mondrian.olap.type.Type;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Definition of the matched <code>CASE</code> MDX operator.
 *
 * Syntax is:
 * <blockquote><pre><code>Case &lt;Expression&gt;
 * When &lt;Expression&gt; Then &lt;Expression&gt;
 * [...]
 * [Else &lt;Expression&gt;]
 * End</code></blockquote>.
 *
 * @see CaseTestFunDef
 * @author jhyde
 * @since Mar 23, 2006
 */
class CaseMatchFunDef extends FunDefBase {
    static final ResolverImpl Resolver = new ResolverImpl();

    private CaseMatchFunDef(FunDef dummyFunDef) {
        super(dummyFunDef);
    }

    public Calc compileCall(ResolvedFunCall call, ExpCompiler compiler) {
        final Exp[] args = call.getArgs();
        final List<Calc> calcList = new ArrayList<Calc>();
        final Calc valueCalc =
                compiler.compileScalar(args[0], true);
        calcList.add(valueCalc);
        final int matchCount = (args.length - 1) / 2;
        final Calc[] matchCalcs = new Calc[matchCount];
        final Calc[] exprCalcs = new Calc[matchCount];
        for (int i = 0, j = 1; i < exprCalcs.length; i++) {
            matchCalcs[i] = compiler.compileScalar(args[j++], true);
            calcList.add(matchCalcs[i]);
            exprCalcs[i] = compiler.compile(args[j++]);
            calcList.add(exprCalcs[i]);
        }
        final Calc defaultCalc =
            args.length % 2 == 0
            ? compiler.compile(args[args.length - 1])
            : ConstantCalc.constantNull(call.getType());
        calcList.add(defaultCalc);
        final Calc[] calcs = calcList.toArray(new Calc[calcList.size()]);

        return new GenericCalc(call) {
            public Object evaluate(Evaluator evaluator) {
                Object value = valueCalc.evaluate(evaluator);
                for (int i = 0; i < matchCalcs.length; i++) {
                    Object match = matchCalcs[i].evaluate(evaluator);
                    if (match.equals(value)) {
                        return exprCalcs[i].evaluate(evaluator);
                    }
                }
                return defaultCalc.evaluate(evaluator);
            }

            public Calc[] getCalcs() {
                return calcs;
            }
        };
    }

    /**
     * Override the default behavior which uses the first arg type as the
     * result type, in the situation for case we use the third parameter
     * as the result type.
     *
     * @param validator Validator
     * @param args Arguments to the call to this operator
     * @return result type of a call this function
     */
    public Type getResultType(Validator validator, Exp[] args) {
        Type thirdArgType =
            args.length > 2
            ? args[2].getType()
            : null;
        Type type = castType(thirdArgType, getReturnCategory());
        if (type != null) {
            return type;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        for (int i = 0; i < args.length; i++) {
          pw.print(" | ");
          args[i].unparse(pw);
        }

        throw Util.newInternal(
            "Cannot deduce type of call to function '" + this.getName()
                + "' DETAILS(ARGCOUNT: " + args.length + ", PARAMS: "
                + sw.toString() + ")");
    }

    private static class ResolverImpl extends ResolverBase {
        private ResolverImpl() {
            super(
                "_CaseMatch",
                "Case <Expression> When <Expression> Then <Expression> [...] [Else <Expression>] End",
                "Evaluates various expressions, and returns the corresponding expression for the first which matches a particular value.",
                Syntax.Case);
        }

        public FunDef resolve(
            Exp[] args,
            Validator validator,
            List<Conversion> conversions)
        {
            if (args.length < 3) {
                return null;
            }
            int valueType = args[0].getCategory();
            int returnType = -1;
            for (int i = 2; i < args.length; i += 2) {
                //If argument return type is null try next one
                if (args[i].getCategory() != 16) {
                    returnType = args[i].getCategory();
                    break;
                }
            }
            if (returnType == -1) {
                return null;
            }
            int clauseCount = (args.length - 1) / 2;
            int j = 0;
            int mismatchingArgs = 0;
            if (!validator.canConvert(j, args[j++], valueType, conversions)) {
                mismatchingArgs++;
            }
            for (int i = 0; i < clauseCount; i++) {
                if (!validator.canConvert(j, args[j++], valueType, conversions))
                {
                    mismatchingArgs++;
                }
                if (!validator.canConvert(
                        j, args[j++], returnType, conversions))
                {
                    mismatchingArgs++;
                }
            }
            if (j < args.length) {
                if (!validator.canConvert(
                        j, args[j++], returnType, conversions))
                {
                    mismatchingArgs++;
                }
            }
            Util.assertTrue(j == args.length);
            if (mismatchingArgs != 0) {
                return null;
            }

            FunDef dummy = createDummyFunDef(this, returnType, args);
            return new CaseMatchFunDef(dummy);
        }

        public boolean requiresExpression(int k) {
            return true;
        }
    }
}

// End CaseMatchFunDef.java
