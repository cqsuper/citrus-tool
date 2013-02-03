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

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;

import com.alibaba.citrus.springext.ConfigurationPoint;

/**
 * ConfigurationPointLabelProvider
 *
 * @author zhiqing.ht
 * @author xuanxiao
 * @version 1.0.0, 2010-07-10
 */
@SuppressWarnings("restriction")
public class ConfigurationPointLabelProvider extends LabelProvider{
	
	public String getText(Object element) {
		if(element instanceof ConfigurationPoint){
			ConfigurationPoint point = (ConfigurationPoint)element;
//			return point.getName()+" -- (defaultElement="+point.getDefaultElementName()+" NSPrefix="+point.getPreferredNsPrefix()+")";
			return point.getName();
		}
		return super.getText(element);
	}
	
	@Override
	public Image getImage(Object element) {
		return JavaPluginImages.get(JavaPluginImages.IMG_OBJS_QUICK_FIX);
	}
}
