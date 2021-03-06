package com.alibaba.ide.plugin.eclipse.springext.resolver;

import static com.alibaba.citrus.util.StringEscapeUtil.*;
import static com.alibaba.ide.plugin.eclipse.springext.SpringExtConstant.*;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import com.alibaba.citrus.springext.Schema;

/**
 * 处理<code>springext://</code> URL协议。
 * 
 * @author Michael Zhou
 */
public class SpringExtURLUtil {
    public static boolean isSpringextURL(URL url) {
        return URL_PROTOCOL.equals(url.getProtocol());
    }

    public static String toSpringextURL(IProject project, Schema schema) {
        try {
            return URL_PROTOCOL + "://" + escapeURL(project.getName(), "UTF-8") + "/" + schema.getName();
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("unbelievable", e);
        }
    }

    public static IProject getProjectFromURL(String url) {
        if (url != null) {
            try {
                return getProjectFromURL(new URL(url));
            } catch (MalformedURLException ignored) {
            }
        }

        return null;
    }

    public static IProject getProjectFromURL(URL url) {
        if (isSpringextURL(url)) {
            String projectName = null;

            try {
                projectName = unescapeURL(url.getHost(), "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }

            if (projectName != null) {
                IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
                return root.getProject(projectName);
            }
        }

        return null;
    }

    public static String getSchemaNameFromURL(URL url) {
        if (isSpringextURL(url)) {
            return url.getPath().replaceAll("^/+", "");
        } else {
            return null;
        }
    }
}
