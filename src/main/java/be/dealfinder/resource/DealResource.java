package be.dealfinder.resource;

import be.dealfinder.dto.DealDTO;
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

@Path("/api/v1/deals")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Deals", description = "Deal operations")
public class DealResource {

    @Inject
    DealService dealService;

    @GET
    @Operation(summary = "Get all deals", description = "Returns deals with optional filters")
    public List<DealDTO> getDeals(
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
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        List<String> retailerList = retailers != null && !retailers.isBlank() 
                ? List.of(retailers.split(",")) : null;
        List<String> categoryList = categories != null && !categories.isBlank() 
                ? List.of(categories.split(",")) : null;

        return dealService.findDeals(
                retailerList,
                categoryList,
                minDiscount,
                search,
                sort,
                order,
                language
        );
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
    @Path("/retailer/{slug}")
    @Operation(summary = "Get deals for a specific retailer")
    public List<DealDTO> getDealsByRetailer(
            @PathParam("slug") String slug,
            
            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        return dealService.findDealsByRetailer(slug, language);
    }
}
