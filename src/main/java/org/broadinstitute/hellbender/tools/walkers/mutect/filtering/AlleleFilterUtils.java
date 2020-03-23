package org.broadinstitute.hellbender.tools.walkers.mutect.filtering;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.vcf.VCFConstants;
import org.broadinstitute.hellbender.tools.walkers.annotator.Annotation;
import org.broadinstitute.hellbender.tools.walkers.annotator.AnnotationUtils;
import org.broadinstitute.hellbender.utils.Utils;
import org.broadinstitute.hellbender.utils.variant.GATKVCFConstants;
import shaded.cloud_nio.com.google.errorprone.annotations.Var;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Helps read and set allele specific filters
 */
public class AlleleFilterUtils {

    public static List<List<String>> decodeASFilters(VariantContext vc) {
        return AnnotationUtils.decodeAnyASListWithRawDelim(vc.getCommonInfo().getAttributeAsString(GATKVCFConstants.AS_FILTER_STATUS_KEY, "")).stream()
                .map(filters -> AnnotationUtils.decodeAnyASList(filters).stream().map(String::trim).collect(Collectors.toList()))
                .collect(Collectors.toList());
    }

    public static String encodeASFilters(List<List<String>> filters) {
        return AnnotationUtils.encodeAnyASListWithRawDelim(filters.stream().map(alleleFilters -> AnnotationUtils.encodeStringList(alleleFilters)).collect(Collectors.toList()));
    }

    public static String getMergedASFilterString(VariantContext vc, List<Boolean> isFiltered, String filterName) {
        List<List<String>> alleleFilters = decodeASFilters(vc);
        Utils.validateArg(isFiltered.size() == alleleFilters.size(), "lists are not the same size");
        ListIterator<Boolean> isFilteredIt = isFiltered.listIterator();

        List<List<String>> updatedFilters = alleleFilters.stream().map(filters -> {
            Boolean filtered = isFilteredIt.next();
            if (filtered) {
                return addFilter(filters, filterName);
            }
            else return filters;
        }).collect(Collectors.toList());
        return encodeASFilters(updatedFilters);
    }

    public static List<String> addFilter(List<String> currentFilters, String newFilter) {
        if (currentFilters.size() == 1 && currentFilters.contains(GATKVCFConstants.SITE_LEVEL_FILTERS)) {
            return Collections.singletonList(newFilter);
        } else {
            List<String> updated = new ArrayList<>();
            updated.addAll(currentFilters);
            updated.add(newFilter);
            return updated;
        }
    }

    /**
     * Sets the filters for each allele and calculates the intersection of the allele filters to set on the variant.
     * PASS if the intersection is empty.
     * @param vc The variant context to build from, however it assumes all relevant filters are set in the alleleFilters collection
     * @param alleleFilters filters to be applied to each allele, the intersection of these filters are applied at the site level
     * @return The updated variant context
     */
    public static VariantContext addAlleleAndComputeSiteFilters(VariantContext vc, List<Set<String>> alleleFilters) {
        String encodedFilters = AlleleFilterUtils.encodeASFilters(alleleFilters.stream().map(
                af -> af.isEmpty() ? Collections.singletonList(GATKVCFConstants.SITE_LEVEL_FILTERS) : af.stream().collect(Collectors.toList())).collect(Collectors.toList()));
        VariantContextBuilder vcb = new VariantContextBuilder(vc).attribute(GATKVCFConstants.AS_FILTER_STATUS_KEY, encodedFilters);

        Set<String> siteFilters = alleleFilters.stream().skip(1)
                .collect(()->new HashSet<>(alleleFilters.get(0)), Set::retainAll, Set::retainAll);

        if (!siteFilters.isEmpty()) {
            vcb.filters(siteFilters);
        } else {
            vcb.passFilters();
        }
        return vcb.make();
    }
}
