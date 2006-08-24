/**
 *
 * Copyright 2006 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.xbean.naming.context;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.Name;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * @version $Rev: 355877 $ $Date: 2005-12-10 18:48:27 -0800 (Sat, 10 Dec 2005) $
 */
public class FederationTest extends AbstractContextTest {
    private Context rootContext;
    private MutableContext unmodifibleContext;
    private Context writableContext;
    private Map rootBindings;
    private Map unmodifibleBindings;
    private Map writableBindings;

    private final class MutableContext extends WritableContext {
        public MutableContext(Map bindings) throws NamingException {
            super("", bindings, ContextAccess.UNMODIFIABLE);
        }

        public void addDeepBinding(Name name, Object value, boolean rebind, boolean createIntermediateContexts) throws NamingException {
            super.addDeepBinding(name, value, rebind, createIntermediateContexts);
        }

        protected void removeDeepBinding(Name name, boolean pruneEmptyContexts) throws NamingException {
            super.removeDeepBinding(name, pruneEmptyContexts);
        }
    }

    public void setUp() throws Exception {
        super.setUp();

        rootBindings = new HashMap();
        rootBindings.put("string", "blah");
        rootBindings.put("nested/context/string", "blah");
        rootBindings.put("java:comp/env/string", "blah");
        rootBindings.put("java:comp/env/one", new Integer(1));
        rootBindings.put("java:comp/env/two", new Integer(2));
        rootBindings.put("java:comp/env/three", new Integer(3));

        rootContext = new WritableContext();
        FederationTest.bind(rootContext, rootBindings);

        assertEq(rootBindings, rootContext);

        unmodifibleBindings = new HashMap();
        unmodifibleBindings.put("string", "blah");
        unmodifibleBindings.put("one", new Integer(1));
        unmodifibleBindings.put("two", new Integer(2));
        unmodifibleBindings.put("three", new Integer(3));

        unmodifibleContext = new MutableContext(unmodifibleBindings);
        assertEq(unmodifibleBindings, unmodifibleContext);

        rootContext.bind("java:comp/unmodifible", unmodifibleContext);
        putAllBindings(rootBindings, "java:comp/unmodifible", unmodifibleBindings);

        writableBindings = new HashMap();
        writableBindings.put("string", "blah");
        writableBindings.put("one", new Integer(1));
        writableBindings.put("two", new Integer(2));
        writableBindings.put("three", new Integer(3));

        writableContext = new WritableContext("", writableBindings);
        assertEq(writableBindings, writableContext);

        rootContext.bind("java:comp/writable", writableContext);
        putAllBindings(rootBindings, "java:comp/writable", writableBindings);
    }

    public void testBasic() throws Exception {
        assertEq(rootBindings, rootContext);
    }

    public void testMutability() throws Exception {
        assertModifiable(rootContext);
        assertUnmodifiable(unmodifibleContext);
        assertModifiable(writableContext);
        assertModifiable(lookupSubcontext(rootContext, "java:comp/unmodifible"));
        assertModifiable(lookupSubcontext(rootContext, "java:comp/writable"));
    }

    public void testBindOverUnmodifiable() throws Exception {
        // bind into root context OVER the unmodifible context
        rootContext.bind("java:comp/unmodifible/TEST", "TEST_VALUE");

        // visible from root context
        rootBindings.put("java:comp/unmodifible/TEST", "TEST_VALUE");
        assertEq(rootBindings, rootContext);

        // not-visible from unmodifibleContext
        assertEq(unmodifibleBindings, unmodifibleContext);
    }

    public void testBindDirectIntoUnmodifiable() throws Exception {
        // bind directly into the unmodifible context
        unmodifibleContext.addDeepBinding(parse("DIRECT"), "DIRECT_VALUE", false, true);

        // visible from root context
        rootBindings.put("java:comp/unmodifible/DIRECT", "DIRECT_VALUE");
        assertEq(rootBindings, rootContext);

        // visible from unmodifibleContext
        unmodifibleBindings.put("DIRECT", "DIRECT_VALUE");
        assertEq(unmodifibleBindings, unmodifibleContext);
    }

    public void testUnbindOverUnmodifiable() throws Exception {
        // unbind value under unmodifible... no exception occurs since unbind is idempotent
        rootContext.unbind("java:comp/unmodifible/three");

        // no change in root context
        assertEq(rootBindings, rootContext);

        // no change in unmodifibleContext
        assertEq(unmodifibleBindings, unmodifibleContext);

        // unbind value deep unmodifible... no exception occurs since unbind is idempotent
        rootContext.unbind("java:comp/unmodifible/three");
    }

    public void testUnbindDirectIntoUnmodifiable() throws Exception {
        // unbind directly from the unmodifible context
        unmodifibleContext.removeDeepBinding(parse("three"), true);

        // visible from root context
        rootBindings.remove("java:comp/unmodifible/three");
        assertEq(rootBindings, rootContext);

        // visible from unmodifibleContext
        unmodifibleBindings.remove("three");
        assertEq(unmodifibleBindings, unmodifibleContext);
    }

    public void testBindIntoWritable() throws Exception {
        // bind into root context OVER the writable context
        rootContext.bind("java:comp/writable/TEST", "TEST_VALUE");

        // visible from root context
        rootBindings.put("java:comp/writable/TEST", "TEST_VALUE");
        assertEq(rootBindings, rootContext);

        // visible from writableContext
        writableBindings.put("TEST", "TEST_VALUE");
        assertEq(writableBindings, writableContext);
    }

    public void testBindDirectIntoWritable() throws Exception {
        // bind directly into the writable context
        writableContext.bind("DIRECT", "DIRECT_VALUE");

        // visible from root context
        rootBindings.put("java:comp/writable/DIRECT", "DIRECT_VALUE");
        assertEq(rootBindings, rootContext);

        // visible from writableContext
        writableBindings.put("DIRECT", "DIRECT_VALUE");
        assertEq(writableBindings, writableContext);
    }

    public void testUnbindOverWritable() throws Exception {
        // unbind value under writable... no exception occurs since unbind is idempotent
        rootContext.unbind("java:comp/writable/three");

        // visible from root context
        rootBindings.remove("java:comp/writable/three");
        assertEq(rootBindings, rootContext);

        // visible from writableContext
        writableBindings.remove("three");
        assertEq(writableBindings, writableContext);

        // unbind value deep writable... no exception occurs since unbind is idempotent
        rootContext.unbind("java:comp/writable/three");
    }

    public void testUnbindDirectIntoWritable() throws Exception {
        // unbind directly from the writable context
        writableContext.unbind("three");

        // visible from root context
        rootBindings.remove("java:comp/writable/three");
        assertEq(rootBindings, rootContext);

        // visible from writableContext
        writableBindings.remove("three");
        assertEq(writableBindings, writableContext);
    }


    public static void putAllBindings(Map rootBindings, String nestedPath, Map nestedBindings) {
        for (Iterator iterator = nestedBindings.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String name = (String) entry.getKey();
            Object value = entry.getValue();
            String fullName = nestedPath + "/" + name;
            rootBindings.put(fullName, value);
        }
    }

    public static void bind(Context context, Map bindings) throws NamingException {
        for (Iterator iterator = bindings.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String name = (String) entry.getKey();
            Object value = entry.getValue();
            Name parsedName = context.getNameParser("").parse(name);
            for (int i =1; i < parsedName.size(); i++) {
                Name contextName = parsedName.getPrefix(i);
                if (!FederationTest.bindingExists(context, contextName)) {
                    context.createSubcontext(contextName);
                }
            }
            context.bind(name, value);
        }
    }

    public static boolean bindingExists(Context context, Name contextName) {
        try {
            return context.lookup(contextName) != null;
        } catch (NamingException e) {
        }
        return false;
    }
}