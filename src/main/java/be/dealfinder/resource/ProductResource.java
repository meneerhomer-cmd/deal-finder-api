package be.dealfinder.resource;

import be.dealfinder.dto.DealDTO;
import be.dealfinder.entity.Product;
import be.dealfinder.service.DealService;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Map;

@Path("/api/v1/products")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "Canonical product registry (aggregated from deals by fingerprint)")
public class ProductResource {

    @Inject
    DealService dealService;

    @GET
    @Operation(summary = "List products, optionally filtered by category or style")
    public Response listProducts(
            @QueryParam("category") String category,
            @QueryParam("style") String style,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("50") int size
    ) {
        StringBuilder where = new StringBuilder("1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (category != null && !category.isBlank()) {
            params.add(category);
            where.append(" AND category = ?").append(params.size());
        }
        if (style != null && !style.isBlank()) {
            params.add(style);
            where.append(" AND style = ?").append(params.size());
        }
        List<Product> products = Product.find(where.toString(),
                Sort.descending("dealCount"), params.toArray())
                .page(page, size).list();
        return Response.ok(products).build();
    }

    @GET
    @Path("/opportunity")
    @Operation(summary = "The single biggest savings opportunity today (home banner); 204 when none qualifies")
    public Response getOpportunity(@QueryParam("lang") @DefaultValue("nl") String language) {
        return dealService.findBiggestOpportunity(language)
                .map(o -> Response.ok(o).build())
                .orElse(Response.noContent().build());
    }

    @GET
    @Path("/{fingerprint}")
    @Operation(summary = "One product plus its active deals across retailers")
    public Response getProduct(
            @PathParam("fingerprint") String fingerprint,
            @QueryParam("lang") @DefaultValue("nl") String language
    ) {
        Product product = Product.findByFingerprint(fingerprint);
        if (product == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "no product for fingerprint " + fingerprint)).build();
        }
        List<DealDTO> deals = dealService.findDealsByFingerprint(fingerprint, language);
        return Response.ok(Map.of("product", product, "deals", deals)).build();
    }
}
