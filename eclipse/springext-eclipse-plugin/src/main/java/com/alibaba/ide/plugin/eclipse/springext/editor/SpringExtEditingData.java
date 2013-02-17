package com.alibaba.ide.plugin.eclipse.springext.editor;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.PlatformObject;

import com.alibaba.ide.plugin.eclipse.springext.schema.ISchemaSetChangeListener;
import com.alibaba.ide.plugin.eclipse.springext.schema.SchemaResourceSet;

/**
 * 代表编辑器内的数据。
 * 
 * @author Michael Zhou
 */
public abstract class SpringExtEditingData extends PlatformObject implements ISchemaSetChangeListener {
    private IProject project;
    private SchemaResourceSet schemas;

    public IProject getProject() {
        return project;
    }

    public void initWithProject(IProject project) {
        this.project = project;
        SchemaResourceSet.addSchemaSetChangeListener(this);
    }

    public SchemaResourceSet getSchemas() {
        return schemas;
    }

    public final void setSchemas(SchemaResourceSet schemas) {
        if (schemas != this.schemas) {
            this.schemas = schemas;

            onSchemaSetChanged();
        }
    }

    protected void onSchemaSetChanged() {
    }

    /**
     * 当schemas被更新时，此方法被调用。
     * <p/>
     * 例如，用户修改了<code>*.bean-definition-parsers</code>文件，或者调整了classpath。
     * 
     * @see ISchemaSetChangeListener
     */
    public final void onSchemaSetChanged(SchemaSetChangeEvent event) {
        // 仅当发生变化的project和当前所编辑的文件所在的project是同一个时，才作反应。
        if (event.getProject().equals(getProject())) {
            setSchemas(SchemaResourceSet.getInstance(getProject()));
        }
    }

    /**
     * 编辑器被关闭时被调用。
     */
    public void dispose() {
        SchemaResourceSet.removeSchemaSetChangeListener(this);
    }
}
