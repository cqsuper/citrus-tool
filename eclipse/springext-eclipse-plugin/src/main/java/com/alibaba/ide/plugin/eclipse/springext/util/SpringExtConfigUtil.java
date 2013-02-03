package com.alibaba.ide.plugin.eclipse.springext.util;

import static com.alibaba.citrus.util.StringUtil.*;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMDocument;

import com.alibaba.citrus.springext.ConfigurationPoint;
import com.alibaba.citrus.springext.ConfigurationPoints;
import com.alibaba.citrus.springext.impl.ConfigurationPointsImpl;

@SuppressWarnings("restriction")
public class SpringExtConfigUtil {
    public static NamespaceDefinition getNamespace(IDOMDocument document, final String namespaceToFind) {
        class NamespaceFinder extends DocumentVisitor {
            NamespaceDefinition nd;

            @Override
            protected void visitElement() {
                parseSchemaLocations();
                visitAttributes();
                visitChildren();
            }

            @Override
            protected void visitAttribute() {
                String nsPrefix = getNamespacePrefix();

                if (nsPrefix != null) {
                    String namespace = trimToEmpty(attribute.getNodeValue());

                    if (namespace.equals(namespaceToFind)) {
                        nd = new NamespaceDefinition(attribute.getNodeValue(), nsPrefix, getSchemaLocations());
                    }
                }
            }

            @Override
            protected boolean continueToNextAttribute() {
                return nd == null;
            }

            @Override
            protected boolean continueToNextChild() {
                return nd == null;
            }
        }

        NamespaceFinder finder = new NamespaceFinder();

        finder.accept(document);

        return finder.nd;
    }

    /**
     * 从dom中取得所有的namespaces和schemaLocations。
     */
    public static NamespaceDefinitions loadNamespaces(IDOMDocument document) {
        final NamespaceDefinitions defs = new NamespaceDefinitions();

        new DocumentVisitor() {
            @Override
            protected void visitElement() {
                parseSchemaLocations();

                visitAttributes();
                visitChildren();
            }

            @Override
            protected void visitAttribute() {
                String nsPrefix = getNamespacePrefix();

                if (nsPrefix != null) {
                    defs.add(new NamespaceDefinition(attribute.getNodeValue(), nsPrefix, getSchemaLocations()));
                }
            }
        }.accept(document);

        return defs;
    }
    
    public static ConfigurationPoint[] getAllConfigurationPoints(IProject project){
		if(project == null){
			return null;
		}
		try {
			String[] classPaths = JavaRuntime.computeDefaultRuntimeClassPath(JavaCore
					.create(project));
			if(classPaths == null){
				return null ;
			}
			ConfigurationPoints points = new ConfigurationPointsImpl(new URLClassLoader(trans2URL(classPaths)));
			Collection<ConfigurationPoint> pointCollection = points.getConfigurationPoints();
			return pointCollection.toArray(new ConfigurationPoint[]{});
		} catch (CoreException e) {
			e.printStackTrace();
			return null;
		}
	}
    
	private static URL[] trans2URL(String[] classPaths) {
		List<URL> list = new ArrayList<URL>();
		for (String s : classPaths) {
			try {
				list.add(new File(s).toURI().toURL());
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
		return list.toArray(new URL[] {});
	}

    public static class NamespaceDefinitions extends LinkedList<NamespaceDefinition> {
    }

    public static class NamespaceDefinition {
        private final String namespace;
        private final String prefix;
        private final String location;

        public NamespaceDefinition(String namespace, String prefix, Map<String, String> schemaLocations) {
            this.namespace = trimToNull(namespace);
            this.prefix = trimToNull(prefix);
            this.location = schemaLocations.get(this.namespace);
        }

        public String getNamespace() {
            return namespace;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getLocation() {
            return location;
        }

        @Override
        public String toString() {
            return "xmlns" + (prefix == null ? "" : ":" + prefix) + "=\"" + namespace + "\"";
        }
    }
}
