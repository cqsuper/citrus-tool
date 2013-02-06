package com.alibaba.ide.plugin.eclipse.springext.extension.editor;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.MultiPageEditorActionBarContributor;

import com.alibaba.ide.plugin.eclipse.springext.SpringExtPlugin;
import com.alibaba.ide.plugin.eclipse.springext.util.dom.DomDocumentUtil;

public class SpringExtConfigEditorContributor extends MultiPageEditorActionBarContributor {
    private final static String MENU_ID = "springext";
    private final static String GROUP_ID = SpringExtConfigEditor.EDITOR_ID;
    private final RemoveUnusedNamespacesAction removeUnusedNamespacesAction = new RemoveUnusedNamespacesAction();
    private final ConvertToUnqualifiedStyleAction convertToUnqualifiedStyleAction = new ConvertToUnqualifiedStyleAction();
    private SpringExtConfig config;

    @Override
    public void contributeToToolBar(IToolBarManager toolBarManager) {
        toolBarManager.add(new GroupMarker(GROUP_ID));
        toolBarManager.appendToGroup(GROUP_ID, removeUnusedNamespacesAction);
        toolBarManager.appendToGroup(GROUP_ID, convertToUnqualifiedStyleAction);
    }

    @Override
    public void contributeToMenu(IMenuManager menuManager) {
        IMenuManager springExtConfigEditorMenu = new MenuManager("SpringExt", MENU_ID);
        menuManager.insertBefore("window", springExtConfigEditorMenu);

        springExtConfigEditorMenu.add(removeUnusedNamespacesAction);
        springExtConfigEditorMenu.add(new Separator());
        springExtConfigEditorMenu.add(convertToUnqualifiedStyleAction);
    }

    @Override
    public void setActiveEditor(IEditorPart part) {
        if (part instanceof SpringExtConfigEditor) {
            config = ((SpringExtConfigEditor) part).getConfig();
        }

        super.setActiveEditor(part);
    }

    @Override
    public void setActivePage(IEditorPart activeEditor) {
        removeUnusedNamespacesAction.setEnabled(true);
    }

    private class RemoveUnusedNamespacesAction extends Action {
        public RemoveUnusedNamespacesAction() {
            super("Remove Unused Namespaces", Action.AS_PUSH_BUTTON);
            setImageDescriptor(SpringExtPlugin.getDefault().getImageRegistry().getDescriptor("clear"));
        }

        @Override
        public void run() {
            if (config != null) {
                DomDocumentUtil.removeUnusedNamespaceDefinitions(config);
                config.getTextViewer().refresh();
            }
        }
    }

    private class ConvertToUnqualifiedStyleAction extends Action {
        public ConvertToUnqualifiedStyleAction() {
            super("Convert to Webx 3.2.x Style", Action.AS_PUSH_BUTTON);
            setImageDescriptor(SpringExtPlugin.getDefault().getImageRegistry().getDescriptor("convert"));
        }

        @Override
        public void run() {
            if (config != null) {
                DomDocumentUtil.convertToUnqualifiedStyle(config);
                config.getTextViewer().refresh();
            }
        }
    }
}
