package com.alibaba.ide.plugin.eclipse.springext;

import static com.alibaba.citrus.util.Assert.*;

import java.io.IOException;
import java.net.URL;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.IWizardContainer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.undo.CreateFileOperation;
import org.eclipse.ui.ide.undo.WorkspaceUndoUtil;
import org.eclipse.wst.sse.core.StructuredModelManager;
import org.eclipse.wst.sse.core.internal.provisional.IStructuredModel;
import org.eclipse.wst.sse.core.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.core.io.Resource;

import com.alibaba.citrus.springext.SourceInfo;

@SuppressWarnings("restriction")
public class SpringExtPluginUtil {
	private static final String	JAR_FILE_PROTOCOL	= "jar:file:"; //$NON-NLS-1$
	public static final String LINE_BR = "\r\n";

    /**
     * 取得指定document所在的project。
     * <p/>
     * 参考实现：
     * {@link org.eclipse.jst.jsp.ui.internal.hyperlink.XMLJavaHyperlinkDetector#createHyperlink(String, IRegion, IDocument)}
     */
    public static IProject getProjectFromDocument(IDocument document) {
        // try file buffers
        ITextFileBuffer textFileBuffer = FileBuffers.getTextFileBufferManager().getTextFileBuffer(document);

        if (textFileBuffer != null) {
            IPath basePath = textFileBuffer.getLocation();

            if (basePath != null && !basePath.isEmpty()) {
                IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(basePath.segment(0));

                if (basePath.segmentCount() > 1 && project.isAccessible()) {
                    return project;
                }
            }
        }

        // fallback to SSE-specific knowledge
        IStructuredModel model = null;

        try {
            model = StructuredModelManager.getModelManager().getExistingModelForRead(document);

            if (model != null) {
                String baseLocation = model.getBaseLocation();

                // URL fixup from the taglib index record
                if (baseLocation.startsWith("jar:/file:")) { //$NON-NLS-1$
                    baseLocation = StringUtils.replace(baseLocation, "jar:/", "jar:"); //$NON-NLS-1$ //$NON-NLS-2$
                }

                /*
                 * Handle opened TLD files from JARs on the Java Build Path by
                 * finding a package fragment root for the same .jar file and
                 * opening the class from there. Note that this might be from a
                 * different Java project's build path than the TLD.
                 */
                if (baseLocation.startsWith(JAR_FILE_PROTOCOL)
                        && baseLocation.indexOf('!') > JAR_FILE_PROTOCOL.length()) {
                    String baseFile = baseLocation.substring(JAR_FILE_PROTOCOL.length(), baseLocation.indexOf('!'));
                    IPath basePath = new Path(baseFile);
                    IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

                    for (IProject project : projects) {
                        try {
                            if (project.isAccessible() && project.hasNature(JavaCore.NATURE_ID)) {
                                IJavaProject javaProject = JavaCore.create(project);

                                if (javaProject.exists()) {
                                    IPackageFragmentRoot root = javaProject.findPackageFragmentRoot(basePath);

                                    if (root != null) {
                                        return javaProject.getProject();
                                    }
                                }
                            }
                        } catch (CoreException ignored) {
                        }
                    }
                } else {
                    IPath basePath = new Path(baseLocation);

                    if (basePath.segmentCount() > 1) {
                        IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(basePath.segment(0));

                        if (project != null && project.isAccessible()) {
                            return project;
                        }
                    }
                }
            }
        } finally {
            if (model != null) {
                model.releaseFromRead();
            }
        }

        // Try get project from editor input
        IEditorInput input = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage().getActiveEditor()
                .getEditorInput();

        return getProjectFromInput(input);
    }

    public static IProject getProjectFromInput(IEditorInput input) {
        if (input instanceof IProjectAware) {
            return ((IProjectAware) input).getProject();
        }

        if (input instanceof IFileEditorInput) {
            return ((IFileEditorInput) input).getFile().getProject();
        }

        return null;
    }

    /**
     * 找到target file所对应的source file。
     */
    public static IFile findSourceFile(@NotNull IFile targetFile) {
        IJavaProject javaProject = getJavaProject(targetFile.getProject(), false);

        if (javaProject != null) {
            // 确保targetFile在output location
            IPath targetPath = targetFile.getFullPath();
            IPath outputLocation = null;

            try {
                outputLocation = javaProject.getOutputLocation();
            } catch (JavaModelException ignored) {
            }

            if (outputLocation != null) {
                // 取得相对路径
                IPath path = targetPath.makeRelativeTo(outputLocation);

                // 从每个source location中查找文件。
                IPackageFragmentRoot[] roots = null;

                try {
                    roots = javaProject.getPackageFragmentRoots();
                } catch (JavaModelException ignored) {
                }

                if (roots != null) {
                    for (IPackageFragmentRoot root : roots) {
                        if (root.getResource() instanceof IFolder) {
                            IFolder folder = (IFolder) root.getResource();
                            IFile sourceFile = folder.getFile(path);

                            if (sourceFile != null && sourceFile.exists()) {
                                return sourceFile;
                            }
                        }
                    }
                }
            }
        }

        return targetFile;
    }

    @Nullable
    public static IJavaProject getJavaProject(IProject project, boolean create) {
        IJavaProject javaProject = null;

        if (project != null) {
            try {
                if (project.hasNature(JavaCore.NATURE_ID)) {
                    javaProject = (IJavaProject) project.getNature(JavaCore.NATURE_ID);
                }

                if (javaProject == null && create) {
                    javaProject = JavaCore.create(project);
                }
            } catch (CoreException ignored) {
            }
        }

        return javaProject;
    }

    public static void logAndDisplay(IStatus status) {
        logAndDisplay(Display.getDefault().getActiveShell(), status);
    }

    public static void logAndDisplay(Shell shell, IStatus status) {
        logAndDisplay(shell, "Error", status);
    }

    public static void logAndDisplay(Shell shell, String title, IStatus status) {
        SpringExtPlugin.getDefault().getLog().log(status);

        if (status.getSeverity() == IStatus.INFO) {
            MessageDialog.openInformation(shell, title, status.getMessage());
        } else {
            MessageDialog.openError(shell, title, status.getMessage());
        }
    }
    
    

	public static IProject getSelectProject(IStructuredSelection selection) {
		Object obj = selection.getFirstElement();
		if (obj != null && obj instanceof IResource) {
			IResource resource = (IResource) obj;
			return resource.getProject();
		}
		return null;
	}

	/**
	 * @param foldPath
	 * @param project
	 * 
	 *            刷新文件夹
	 */
	public static void refreshFolder(String foldPath, IProject project) {
		try {
			project.getFolder(foldPath).refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		} catch (Exception e) {
		}
	}

	/**
	 * @param foldPath
	 * @param project
	 * 
	 *            刷新文件夹
	 */
	public static void refreshFolder(IFolder folder) {
		try {
			folder.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
		} catch (Exception e) {
		}
	}

	/**
	 * 检查"/src/main/resources/"是否存在，若不存在则创建 同时检查其是否在构建路径中，不在则加入到构建路径
	 * 
	 * @param project
	 */
	@SuppressWarnings("restriction")
	public static void checkSrcMetaExsit(IProject project) {
		if (null == project) {
			return;
		}
		// 如果文件夹不存在，则创建文件夹
		File projectFile = project.getLocation().toFile();
		String srcResourceFolderPath = "/src/main/resources/";
		String srcMetaFolderPath = "/src/main/resources/META-INF/";
		File fileSrcMeta = new File(projectFile, srcMetaFolderPath);
		if (!fileSrcMeta.exists()) {
			fileSrcMeta.mkdirs();
			refreshFolder(srcMetaFolderPath, project);
		}
		IFolder srcResourceFolder = project.getFolder(srcResourceFolderPath);
		IJavaProject javaProject = JavaCore.create(project);
		boolean srcMeTaClassPathExsit = false;
		IPackageFragmentRoot[] packageFragmentRoots = null;
		try {
			packageFragmentRoots = javaProject.getPackageFragmentRoots();
		} catch (JavaModelException e) {
		}
		if (null != packageFragmentRoots && packageFragmentRoots.length > 0) {
			for (IPackageFragmentRoot packageFragmentRoot : packageFragmentRoots) {
				IResource resource = packageFragmentRoot.getResource();
				if (null != resource && resource instanceof IFolder) {
					String resoucePath = resource.getProjectRelativePath().toString();
					if (resoucePath.toLowerCase().equals("src/main/resources")) {
						srcMeTaClassPathExsit = true;
						break;
					}
				}
			}
		}
		if (srcMeTaClassPathExsit) {
			return;
		}
		IPath path = srcResourceFolder.getFullPath();
		CPListElement cpListElement = new CPListElement(javaProject, IClasspathEntry.CPE_SOURCE, path,
		        srcResourceFolder);
		IClasspathEntry[] cpFormer = javaProject.readRawClasspath();
		IClasspathEntry[] cpAfter = null;
		int index = 0;
		if (cpFormer != null) {
			int cpSize = cpFormer.length + 1;
			cpAfter = new IClasspathEntry[cpSize];
			for (; index < cpFormer.length; index++) {
				cpAfter[index] = cpFormer[index];
			}
		} else {
			cpAfter = new IClasspathEntry[1];
		}
		IClasspathEntry ce = null;
		try {
			ce = cpListElement.getClasspathEntry();
		} catch (Exception e1) {
		}
		cpAfter[index] = ce;
		try {
			javaProject.setRawClasspath(cpAfter, javaProject.getOutputLocation(), new SubProgressMonitor(
			        new NullProgressMonitor(), 2));
		} catch (JavaModelException e2) {
		}

	}


	/**
	 * 如果文件存在，则追加内容；否则，创建文件并写入内容
	 */
	public static void createFileIfNesscary(String parent, String fileName, final String content,
	        IStructuredSelection selection, final Shell shell, final String des, IWizardContainer container) {

		final IFile file = getFileHandle(parent, fileName, selection);
		if (file.exists()) {
			FileOutputStream outStream = null;
			try {
				outStream = new FileOutputStream(file.getLocation().toString(), true);
				outStream.write(("\r\n" + content).getBytes());
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (outStream != null) {
					try {
						outStream.close();
					} catch (IOException e) {
					}
				}
			}
			try {
				getSelectProject(selection).getFolder(parent).refreshLocal(IResource.DEPTH_INFINITE,
				        new NullProgressMonitor());
			} catch (CoreException e) {
				e.printStackTrace();
			}
		} else {
			IRunnableWithProgress op = new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					CreateFileOperation op = new CreateFileOperation(file, null, new ByteArrayInputStream(
					        content.getBytes()), des);
					try {
						PlatformUI.getWorkbench().getOperationSupport().getOperationHistory()
						        .execute(op, monitor, WorkspaceUndoUtil.getUIInfoAdapter(shell));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			};
			try {
				container.run(true, true, op);
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}

	}

	public static IFile getFileHandle(String parent, String xsdName, IStructuredSelection selection) {
		IProject project = getSelectProject(selection);
		IFile file = project.getFile(parent + "/" + xsdName);
		return file;
	}
	
	public static String formatXsd(String template,String... obj){
		String temp = getFileString(template);
		if(temp.length() > 0){
			MessageFormat format = new MessageFormat(temp);
			return format.format(obj);
		}
		return null;
	}

	/**
	 * @return 获取文件内容，返回String
	 */
	public static String getFileString(String template) {
		InputStream stream = SpringExtPluginUtil.class.getClassLoader().getResourceAsStream(template);
		BufferedReader br = null;
		StringBuilder sb = new StringBuilder();
		try {
			br = new BufferedReader(new InputStreamReader(stream,"UTF-8"));
			String line = null;
			while((line = br.readLine()) != null){
				sb.append(line);
				sb.append(LINE_BR);
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
			if(br != null){
				try {
					br.close();
				} catch (IOException e) {
				}
			}
		}
		return sb.toString();
	}
	
	/**
	 * @param selection
	 * @return 通过selection获取到当前Javaproject
	 */
	public static IJavaProject getSelectJavaProject(IStructuredSelection selection){
		Object obj = selection.getFirstElement();
		if(obj != null && obj instanceof IResource){
			IResource resource = (IResource)obj;
			return JavaCore.create( resource.getProject());
		}
		return null;
	}
    public static URL getSourceURL(Object object) {
        assertTrue(object instanceof SourceInfo<?>, "not a source info");
        return getSourceURL((SourceInfo<?>) object);
    }

    public static URL getSourceURL(SourceInfo<?> sourceInfo) {
        Resource resource = (Resource) sourceInfo.getSource();
        URL url = null;

        try {
            url = resource.getURL();
        } catch (IOException ignored) {
        }

        return url;
    }
}
