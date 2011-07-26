/*
 * Copyright (c) 2011, The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.broadinstitute.sting.commandline;

import org.broadinstitute.sting.gatk.contexts.ReferenceContext;
import org.broadinstitute.sting.gatk.refdata.RefMetaDataTracker;
import org.broadinstitute.sting.utils.GenomeLoc;
import org.broadinstitute.sting.utils.variantcontext.VariantContext;

import java.io.File;
import java.util.List;

/**
 *
 */
// TODO -- should have a derived class called VariantContentRodBinding with simple accessors
public class RodBinding {
    final String variableName;
    final File sourceFile;

    public RodBinding(final String variableName, final File sourceFile) {
        this.variableName = variableName;
        this.sourceFile = sourceFile;
    }

    public String getVariableName() {
        return variableName;
    }

    public File getSourceFile() {
        return sourceFile;
    }

    public List<Object> getAll(RefMetaDataTracker tracker) {
        return tracker.getReferenceMetaData(variableName);
    }

    public VariantContext getVariantContext(RefMetaDataTracker tracker, ReferenceContext ref, GenomeLoc loc) {
        return tracker.getVariantContext(ref, variableName, loc);
    }

    public String toString() {
        return String.format("(RodBinding name=%s source=%s)", variableName, sourceFile);
    }

}
