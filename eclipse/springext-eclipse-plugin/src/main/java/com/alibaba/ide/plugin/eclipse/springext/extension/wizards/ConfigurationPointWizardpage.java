/*
 * Copyright 2010 Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.alibaba.ide.plugin.eclipse.springext.extension.wizards;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.undo.CreateFileOperation;
import org.eclipse.ui.ide.undo.WorkspaceUndoUtil;

import com.alibaba.ide.plugin.eclipse.springext.util.SpringExtPluginUtil;


/**
 * �½�Springext Configuration Point��ҳ
 *
 * @author zhiqing.ht
 * @author xuanxiao
 * @version 1.0.0, 2010-07-10
 */
public class ConfigurationPointWizardpage extends WizardPage {
	
	public static final String LINE_BR = "\r\n";

	private IStructuredSelection selection = null;
	
	private Text cpNameText = null;
	
	private Text tNSNameText = null;
	
	private Text defaultElementText = null;
	
	private Text nsPrefixText = null;
	
	private static final String DEFAULT_PREFIX = "http://www.alibaba.com/schema/";
	
	protected ConfigurationPointWizardpage(String pageName, String title,
			ImageDescriptor titleImage,IStructuredSelection selection) {
		super(pageName, title, titleImage);
		this.selection = selection;
		setDescription("新建Configuration Point。它代表了一个扩展点，\n" +
				"其他开发者可以创建Contribution捐献给它，从而扩展它的功能。");
	}

	public void createControl(Composite parent) {
		
		Composite comp = new Composite(parent,SWT.NULL);
		comp.setLayout(new GridLayout(1,false));
		setControl(comp);
		Label l = new Label(comp,SWT.NULL);
		l.setText("Configuration Point名称：（格式形如：xxx或services/template/engines - 通常用英文复数）");
		cpNameText = new Text(comp,SWT.SINGLE | SWT.BORDER);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		cpNameText.setLayoutData(data);
		l = new Label(comp,SWT.NULL);
		l.setText("Target Namespace：（必须以Configuration Point名称结尾）");
		tNSNameText = new Text(comp,SWT.SINGLE | SWT.BORDER);
		tNSNameText.setLayoutData(data);
		l = new Label(comp,SWT.NULL);
		l.setText("Default Element：（可选）");
		defaultElementText = new Text(comp,SWT.SINGLE | SWT.BORDER);
		defaultElementText.setLayoutData(data);
		l = new Label(comp,SWT.NULL);
		l.setText("Namespace 推荐前缀：（可选）");
		nsPrefixText = new Text(comp,SWT.SINGLE | SWT.BORDER);
		nsPrefixText.setLayoutData(data);
		initListeners();
	}
	
	private void initListeners() {
		cpNameText.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				tNSNameText.setText(DEFAULT_PREFIX+cpNameText.getText());
			}
		});
	}

	public boolean validate(){
		String cpName = cpNameText.getText();
		if( null == cpName || cpName.trim().equals("")){
			setErrorMessage("Configuration Point Name ����Ϊ�գ�");
			return false;
		}else {

			if( cpName.startsWith("/") || cpName.startsWith("\\") ){
				setErrorMessage("Configuration Point Name 格式必须为xxx或xxx/yyy/zzz !");
				return false;
			}
			if(cpName.indexOf("\\") >= 0 ){
				setErrorMessage("非法的Configuration Point Name ,不能有'\\'非法字符!");
				return false;
			}
		}
		String tNSName = tNSNameText.getText();
		if( null == tNSName || tNSName.trim().equals("")){
			setErrorMessage("Target Namespace 不能为空！");
			return false;
		}else {
			if(!tNSName.trim().endsWith(cpName)){
				setErrorMessage("Target Namespace 必须以"+cpName+"结尾！");
				return false;
			}
		}
		
		return true;
	}
	
	public void createConfigurationPoint(){
		IProject project = SpringExtPluginUtil.getSelectProject(selection);
		SpringExtPluginUtil.checkSrcMetaExsit(project);
		final IFile file = getFileHandle("spring.configuration-points");
		String cpName = cpNameText.getText();
		String tNSName = tNSNameText.getText();
		String defaultElement = defaultElementText.getText();
		String nsPrefix = nsPrefixText.getText();
		String tempString = cpName+"="+tNSName;
		if(defaultElement != null && defaultElement.length() > 0){
		    tempString += "; defaultElement="+defaultElement;
		}
		if(nsPrefix != null && nsPrefix.length() > 0){
		    tempString +=  "; nsPrefix="+nsPrefix;
		}
		final String text = tempString;
		if(file.exists()){
			FileOutputStream outStream = null;
			try {
				outStream = new FileOutputStream(file.getLocation().toString(),true);
				outStream.write((LINE_BR+text).getBytes());
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}finally{
				if(outStream != null){
					try {
						outStream.close();
					} catch (IOException e) {
					}
				}
			}
			try {
				SpringExtPluginUtil.getSelectProject(selection).getFolder("/src/main/resources/META-INF").refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			} catch (CoreException e) {
				e.printStackTrace();
			}
			
		
		}else{
			IRunnableWithProgress op = new IRunnableWithProgress() {

				public void run(IProgressMonitor monitor)
						throws InvocationTargetException, InterruptedException {
					CreateFileOperation op = new CreateFileOperation(file,
							null, new ByteArrayInputStream(text.getBytes()),
							"新建Configuration point...");
						try {
							PlatformUI.getWorkbench().getOperationSupport()
									.getOperationHistory().execute(
											op,
											monitor,
											WorkspaceUndoUtil
													.getUIInfoAdapter(getShell()));
						} catch (ExecutionException e) {
							e.printStackTrace();
						}
				}
				
			};
			try {
				getContainer().run(true, true,op);
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	private IFile getFileHandle(String xsdName) {
		IProject project = SpringExtPluginUtil.getSelectProject(selection);
		IFile file = project.getFile("/src/main/resources/META-INF/"+xsdName);
		return file;
	}

}
