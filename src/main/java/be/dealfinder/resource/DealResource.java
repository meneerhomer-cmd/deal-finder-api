package be.dealfinder.resource;

import be.dealfinder.dto.DealDTO;
import be.dealfinder.dto.PagedResponse;
import be.dealfinder.dto.PriceHistoryDTO;
import be.dealfinder.entity.Deal;
import be.dealfinder.entity.PriceHistory;
import be.dealfinder.service.DealService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/v1/deals")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Deals", description = "Deal operations")
public class DealResource {

    @Inject
    DealService dealService;

    @GET
    @Operation(summary = "Get all deals", description = "Returns deals with optional filters. Add page/size for pagination.")
    public Response getDeals(
            @Parameter(description = "Filter by retailer slugs (comma-separated)")
            @QueryParam("retailer") String retailers,

            @Parameter(description = "Filter by category slugs (comma-separated)")
            @QueryParam("category") String categories,

            @Parameter(description = "Minimum discount percentage (20-100)")
            @QueryParam("minDiscount") Integer minDiscount,

            @Parameter(description = "Search by product name")
            @QueryParam("search") String search,

            @Parameter(description = "Sort by: discount, price, expiry, name")
            @QueryParam("sort") @DefaultValue("discount") String sort,

            @Parameter(description = "Sort order: asc, desc")
            @QueryParam("order") String order,

            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language,

            @Parameter(description = "Page number (0-based). Omit for unpaginated.")
            @QueryParam("page") Integer page,

            @Parameter(description = "Page size (default 20)")
            @QueryParam("size") @DefaultValue("20") int size
    ) {
        List<String> retailerList = retailers != null && !retailers.isBlank()
                ? List.of(retailers.split(",")) : null;
        List<String> categoryList = categories != null && !categories.isBlank()
                ? List.of(categories.split(",")) : null;

        if (page != null) {
            PagedResponse<DealDTO> paged = dealService.findDealsPaged(
                    retailerList, categoryList, minDiscount, search, sort, order, language,
                    page, size);
            return Response.ok(paged).build();
        }

        return Response.ok(dealService.findDeals(
                retailerList, categoryList, minDiscount, search, sort, order, language
        )).build();
    }

    @GET
    @Path("/grouped")
    @Operation(summary = "Get deals grouped by retailer")
    public Map<String, List<DealDTO>> getDealsGrouped(
            @Parameter(description = "Minimum discount percentage")
            @QueryParam("minDiscount") Integer minDiscount,
            
            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        return dealService.findDealsGroupedByRetailer(minDiscount, language);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get deal by ID")
    public Response getDeal(
            @PathParam("id") Long id,

            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        return dealService.findDealById(id, language)
                .map(deal -> Response.ok(deal).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/{id}/cross-retailer-matches")
    @Operation(summary = "Same product, other retailers, sorted by price ASC")
    public List<DealDTO> getCrossRetailerMatches(
            @PathParam("id") Long id,
            @QueryParam("lang") @DefaultValue("nl") String language
    ) {
        return dealService.findCrossRetailerMatches(id, language);
    }

    @GET
    @Path("/{id}/substitute")
    @Operation(summary = "One cheaper same-style alternative from another brand (or 204 when none qualifies)")
    public Response getSubstitute(
            @PathParam("id") Long id,
            @QueryParam("lang") @DefaultValue("nl") String language
    ) {
        return dealService.findSubstitute(id, language)
                .map(deal -> Response.ok(deal).build())
                .orElse(Response.noContent().build());
    }

    @POST
    @Path("/{id}/wrong-match")
    @Operation(summary = "Report that a cross-retailer / substitute suggestion is a wrong match")
    public Response reportWrongMatch(
            @PathParam("id") Long id,
            @Parameter(description = "The deal id that was wrongly matched to this one")
            @QueryParam("targetId") Long targetId
    ) {
        if (targetId == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        dealService.reportWrongMatch(id, targetId);
        return Response.accepted().build();
    }

    @GET
    @Path("/retailer/{slug}")
    @Operation(summary = "Get deals for a specific retailer")
    public List<DealDTO> getDealsByRetailer(
            @PathParam("slug") String slug,

            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        return dealService.findDealsByRetailer(slug, language);
    }

    @GET
    @Path("/{id}/price-history")
    @Operation(summary = "Get price history for a deal")
    public Response getPriceHistory(@PathParam("id") Long id) {
        Deal deal = Deal.findById(id);
        if (deal == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        String normalized = PriceHistory.normalizeProductName(deal.productName);
        List<PriceHistoryDTO> history = PriceHistory.findByProductAndRetailerLast90Days(normalized, deal.retailer.id)
                .stream()
                .map(PriceHistoryDTO::from)
                .collect(Collectors.toList());
        return Response.ok(history).build();
    }
}
