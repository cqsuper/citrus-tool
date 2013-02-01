package com.alibaba.ide.plugin.eclipse.springext.extension.hyperlink.detector;

import static com.alibaba.ide.plugin.eclipse.springext.util.SpringExtPluginUtil.getJavaProject;
import static com.alibaba.ide.plugin.eclipse.springext.util.SpringExtPluginUtil.getProjectFromDocument;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IProject;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector;
import org.eclipse.jface.text.hyperlink.IHyperlink;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;

import com.alibaba.citrus.springext.ContributionType;

public class JavaHyperlinkDetector extends AbstractHyperlinkDetector {

	private final static Pattern	contirbutePattern	= Pattern.compile("[\\w]+\\s*=\\s*+([.$\\w]+)");

	public IHyperlink[] detectHyperlinks(ITextViewer textViewer, IRegion region, boolean canShowMultipleHyperlinks) {
		if (region == null || textViewer == null) {
			return null;
		}

		IDocument document = textViewer.getDocument();

		if (document == null) {
			return null;
		}
		if (!checkContributionFile()) {
			return null;
		}

		int offset = region.getOffset();
		IRegion lineInfo;
		String line;

		try {
			lineInfo = document.getLineInformationOfOffset(offset);
			line = document.get(lineInfo.getOffset(), lineInfo.getLength());
		} catch (BadLocationException e) {
			return null;
		}

		Matcher matcher = contirbutePattern.matcher(line);

		while (matcher.find()) {
			String className = matcher.group(1);
			return createJavaHyperlinks(document, className, new Region(lineInfo.getOffset() + matcher.start(1),
			        className.length()));

		}

		return null;
	}

	// 得到当前打开的文件的后缀
	private String getFileExtension() {
		IEditorPart editorPart = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor();
		if (editorPart != null) {
			String fileName = editorPart.getTitle();
			int index = fileName.indexOf(".");
			if (index > 0) {
				return fileName.substring(index);
			}
		}
		return null;

	}

	// 判断是否是捐献文件
	private boolean checkContributionFile() {
		String fileExtension = getFileExtension();
		for (ContributionType ct : ContributionType.values()) {
			if (ct.getContributionsLocationSuffix().equals(fileExtension)) {
				return true;
			}
		}
		return false;
	}

	private IHyperlink[] createJavaHyperlinks(IDocument document, String className, Region region) {
		IProject project = getProjectFromDocument(document);

		if (project != null) {
			IJavaProject javaProject = getJavaProject(project, true);

			if (javaProject != null && javaProject.exists()) {
				try {
					IJavaElement element = javaProject.findType(className);

					if (element != null && element.exists()) {
						return new IHyperlink[] { new JavaElementHyperlink(region, element) };
					}
				} catch (JavaModelException ignore) {
				}
			}
		}

		return null;
	}

	public static class JavaElementHyperlink implements IHyperlink {
		private final IJavaElement	element;
		private final IRegion		region;

		JavaElementHyperlink(IRegion region, IJavaElement element) {
			this.region = region;
			this.element = element;
		}

		public IRegion getHyperlinkRegion() {
			return region;
		}

		public String getHyperlinkText() {
			return String.format("Open '%s'", element.getElementName());
		}

		public String getTypeLabel() {
			return null;
		}

		public void open() {
			try {
				JavaUI.openInEditor(element);
			} catch (Exception ignored) {
			}
		}
	}
}
