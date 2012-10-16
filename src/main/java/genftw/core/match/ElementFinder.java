/*
 * Copyright 2011 GenFTW contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package genftw.core.match;

import genftw.api.Where;
import genftw.core.util.HashCodeUtil;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementScanner6;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Element visitor that scans root elements, looking for elements matching given criteria.
 */
public class ElementFinder extends ElementScanner6<Void, Set<Where>> {

    private final Elements elementUtils;
    private final Types typeUtils;
    private final ElementMatcher elementMatcher;
    private final Pattern elementPackagePattern;
    private final Set<Element> elementsScanned;
    private final Map<Integer, Set<Element>> elementsFound;

    public ElementFinder(Elements elementUtils, Types typeUtils,
            ElementMatcher elementMatcher, String elementPackageFilter) {
        this.elementUtils = elementUtils;
        this.typeUtils = typeUtils;
        this.elementMatcher = elementMatcher;
        this.elementPackagePattern = Pattern.compile(elementPackageFilter);
        this.elementsScanned = new HashSet<Element>();
        this.elementsFound = new HashMap<Integer, Set<Element>>();
    }

    public Void scan(Set<? extends Element> rootElements, Set<Where> matchDefinitions) {
        elementsScanned.clear();
        elementsFound.clear();
        return super.scan(rootElements, matchDefinitions);
    }

    public Element[] getElementsFound(Where def) {
        Set<Element> found = elementsFound.get(getKey(def));
        return found != null ? found.toArray(new Element[0]) : new Element[0];
    }

    boolean packageIncluded(PackageElement pkg) {
        return elementPackagePattern.matcher(pkg.getQualifiedName().toString()).matches();
    }

    void addElement(Element elm, Where def) {
        int key = getKey(def);

        if (elementsFound.get(key) == null) {
            elementsFound.put(key, new HashSet<Element>());
        }

        elementsFound.get(key).add(elm);
    }

    void matchElement(Element elm, Set<Where> matchDefinitions) {
        for (Where def : matchDefinitions) {
            if (elementMatcher.matches(elm, def)) {
                addElement(elm, def);
            }
        }
    }

    /**
     * Returns the hash code (key) of the given match definition.
     * <p>
     * We are computing annotation hash code manually because:
     * <p>
     * <ul>
     * <li>JSR-269 tools might provide annotation proxies
     * <li>some parts of match definition are not significant to element matching process
     * </ul>
     */
    public int getKey(Where def) {
        int result = HashCodeUtil.SEED;
        result = HashCodeUtil.hash(result, def.kind());
        result = HashCodeUtil.hash(result, def.modifiers());
        result = HashCodeUtil.hash(result, def.simpleNameMatches());
	    result = HashCodeUtil.hash(result, def.annotationsAll());
	    result = HashCodeUtil.hash(result, def.annotationsAny());
	    result = HashCodeUtil.hash(result, def.metaData());
        return result;
    }

    @Override
    public Void scan(Element e, Set<Where> p) {
        if (!elementsScanned.contains(e)) {
            // Remember scanned elements to avoid infinite recursion
            elementsScanned.add(e);
            return super.scan(e, p);
        }

        return DEFAULT_VALUE;
    }

    @Override
    public Void visitPackage(PackageElement e, Set<Where> p) {
        // Apply package filter
        if (!packageIncluded(e)) {
            return DEFAULT_VALUE;
        }

        // Match package
        matchElement(e, p);

        // Match enclosed elements
        return scan(e.getEnclosedElements(), p);
    }

    @Override
    public Void visitType(TypeElement e, Set<Where> p) {
        // Apply package filter
        if (!packageIncluded(elementUtils.getPackageOf(e))) {
            return DEFAULT_VALUE;
        }

        // Match type
        matchElement(e, p);

        // Match superclass
        Element superclass = typeUtils.asElement(e.getSuperclass());
        if (superclass != null) {
            scan(superclass, p);
        }

        // Match interfaces
        for (TypeMirror t : e.getInterfaces()) {
            Element iface = typeUtils.asElement(t);
            scan(iface, p);
        }

        // Match type parameters
        for (TypeParameterElement tp : e.getTypeParameters()) {
            scan(tp, p);
        }

        // Match enclosed elements
        return scan(e.getEnclosedElements(), p);
    }

    @Override
    public Void visitExecutable(ExecutableElement e, Set<Where> p) {
        // Match executable
        matchElement(e, p);

        // Match type parameters
        for (TypeParameterElement tp : e.getTypeParameters()) {
            scan(tp, p);
        }

        // Match return type
        Element retType = typeUtils.asElement(e.getReturnType());
        if (retType != null) {
            scan(retType, p);
        }

        // Match parameters
        for (VariableElement v : e.getParameters()) {
            scan(v, p);
        }

        // Match thrown types
        for (TypeMirror t : e.getThrownTypes()) {
            Element throwable = typeUtils.asElement(t);
            scan(throwable, p);
        }

        return DEFAULT_VALUE;
    }

    @Override
    public Void visitVariable(VariableElement e, Set<Where> p) {
        // Match variable
        matchElement(e, p);

        return DEFAULT_VALUE;
    }

    @Override
    public Void visitTypeParameter(TypeParameterElement e, Set<Where> p) {
        // Match type parameter
        matchElement(e, p);

        // Match parameterized element
        matchElement(e.getGenericElement(), p);

        // Match parameter bounds
        for (TypeMirror t : e.getBounds()) {
            Element bound = typeUtils.asElement(t);
            scan(bound, p);
        }

        return DEFAULT_VALUE;
    }

}
