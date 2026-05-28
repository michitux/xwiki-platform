/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.rendering.internal.macro.jexl;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.commons.jexl3.introspection.JexlPermissions;

/**
 * JEXL permissions approximating the Velocity sandbox used in XWiki while restricting constructors and static access to
 * explicit allow-lists.
 *
 * @version $Id$
 * @since 18.4.0
 */
class VelocityLikeJexlPermissions extends JexlPermissions.Delegate
{
    private static final Set<String> RESTRICTED_PACKAGES = Set.of("java.lang.reflect");

    private static final Set<String> RESTRICTED_CLASSES = Set.of(
        "java.lang.Compiler",
        "java.lang.InheritableThreadLocal",
        "java.lang.Package",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.Reflect",
        "java.lang.Runtime",
        "java.lang.RuntimePermission",
        "java.lang.SecurityManager",
        "java.lang.System",
        "java.lang.ThreadGroup",
        "java.lang.ThreadLocal",
        "java.net.Socket",
        "javax.management.MBeanServer",
        "javax.script.ScriptEngine");

    private static final Map<Class<?>, Set<String>> METHOD_ALLOWLIST = Map.of(
        Class.class, Set.of(
            "getname",
            "getsimplename",
            "isarray",
            "isassignablefrom",
            "isenum",
            "isinstance",
            "isinterface",
            "islocalclass",
            "ismemberclass",
            "isprimitive",
            "issynthetic",
            "getenumconstants"),
        File.class, Set.of(
            "canexecute",
            "canread",
            "canwrite",
            "compareto",
            "createtempfile",
            "equals",
            "getabsolutefile",
            "getabsolutepath",
            "getcanonicalfile",
            "getcanonicalpath",
            "getclass",
            "getfreespace",
            "getname",
            "getparent",
            "getparentfile",
            "getpath",
            "gettotalspace",
            "getusablespace",
            "hashcode",
            "isabsolute",
            "isdirectory",
            "isfile",
            "ishidden",
            "lastmodified",
            "length",
            "topath",
            "tostring",
            "touri",
            "tourl"));

    private static final List<RestrictedMethod> RESTRICTED_METHODS = List.of(
        new RestrictedMethod(org.apache.velocity.app.VelocityEngine.class, "init"),
        new RestrictedMethod(org.apache.velocity.app.VelocityEngine.class, "reset"));

    private final Set<Class<?>> constructorAllowlist;

    private final Set<Class<?>> staticAccessAllowlist;

    VelocityLikeJexlPermissions(Collection<Class<?>> constructorAllowlist, Collection<Class<?>> staticAccessAllowlist)
    {
        super(JexlPermissions.RESTRICTED);

        this.constructorAllowlist = Set.copyOf(constructorAllowlist);
        this.staticAccessAllowlist = Set.copyOf(staticAccessAllowlist);
    }

    @Override
    public boolean allow(Class<?> clazz)
    {
        if (clazz == null) {
            return false;
        }

        if (findMethodAllowlist(clazz) != null) {
            return true;
        }

        if (this.constructorAllowlist.contains(clazz) || this.staticAccessAllowlist.contains(clazz)) {
            return true;
        }

        return !isRestrictedClass(clazz) && super.allow(clazz);
    }

    @Override
    public boolean allow(Constructor<?> ctor)
    {
        return validate(ctor)
            && this.constructorAllowlist.contains(ctor.getDeclaringClass())
            && super.allow(ctor);
    }

    @Override
    public boolean allow(Field field)
    {
        return validate(field) && allow(field.getDeclaringClass(), field);
    }

    @Override
    public boolean allow(Class<?> clazz, Field field)
    {
        if (!validate(field) || !allow(clazz)) {
            return false;
        }

        if (Modifier.isStatic(field.getModifiers())) {
            return this.staticAccessAllowlist.contains(clazz) && super.allow(clazz, field);
        }

        return findMethodAllowlist(clazz) == null && !isRestrictedClass(clazz) && super.allow(clazz, field);
    }

    @Override
    public boolean allow(Method method)
    {
        return validate(method) && allow(method.getDeclaringClass(), method);
    }

    @Override
    public boolean allow(Class<?> clazz, Method method)
    {
        if (!validate(method) || !allow(clazz)) {
            return false;
        }

        String methodName = method.getName();
        if ("wait".equals(methodName) || "notify".equals(methodName) || "notifyAll".equals(methodName)) {
            return false;
        }

        Set<String> allowlist = findMethodAllowlist(clazz);
        if (allowlist != null) {
            return allowlist.contains(methodName.toLowerCase(Locale.ROOT));
        }

        if (isRestrictedMethod(clazz, methodName)) {
            return false;
        }

        if (Modifier.isStatic(method.getModifiers())) {
            return this.staticAccessAllowlist.contains(clazz) && super.allow(clazz, method);
        }

        return !isRestrictedClass(clazz) && super.allow(clazz, method);
    }

    private Set<String> findMethodAllowlist(Class<?> clazz)
    {
        for (Map.Entry<Class<?>, Set<String>> entry : METHOD_ALLOWLIST.entrySet()) {
            if (entry.getKey().isAssignableFrom(clazz)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean isRestrictedClass(Class<?> clazz)
    {
        if (ClassLoader.class.isAssignableFrom(clazz) || Thread.class.isAssignableFrom(clazz)) {
            return true;
        }

        if (Class.class.isAssignableFrom(clazz)) {
            return true;
        }

        String className = clazz.getName();
        if (className.startsWith("[L") && className.endsWith(";")) {
            className = className.substring(2, className.length() - 1);
        }

        int dot = className.lastIndexOf('.');
        String packageName = dot >= 0 ? className.substring(0, dot) : "";

        return RESTRICTED_PACKAGES.contains(packageName) || RESTRICTED_CLASSES.contains(className);
    }

    private boolean isRestrictedMethod(Class<?> clazz, String methodName)
    {
        for (RestrictedMethod restrictedMethod : RESTRICTED_METHODS) {
            if (restrictedMethod.methodName.equals(methodName)
                && restrictedMethod.declaringClass.isAssignableFrom(clazz)) {
                return true;
            }
        }

        return false;
    }

    private static final class RestrictedMethod
    {
        private final Class<?> declaringClass;

        private final String methodName;

        private RestrictedMethod(Class<?> declaringClass, String methodName)
        {
            this.declaringClass = declaringClass;
            this.methodName = methodName;
        }
    }
}
