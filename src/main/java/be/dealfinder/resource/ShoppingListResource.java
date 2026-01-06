package be.dealfinder.resource;

import be.dealfinder.dto.ShoppingListItemDTO;
import be.dealfinder.service.ShoppingListService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/shopping-list")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Shopping List", description = "Shopping list operations")
public class ShoppingListResource {

    @Inject
    ShoppingListService shoppingListService;

    @GET
    @Operation(summary = "Get shopping list for session")
    public List<ShoppingListItemDTO> getShoppingList(
            @Parameter(description = "Session ID", required = true)
            @HeaderParam("X-Session-Id") String sessionId,
            
            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        validateSessionId(sessionId);
        return shoppingListService.getShoppingList(sessionId, language);
    }

    @GET
    @Path("/active")
    @Operation(summary = "Get active (not purchased) items")
    public List<ShoppingListItemDTO> getActiveItems(
            @HeaderParam("X-Session-Id") String sessionId,
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        validateSessionId(sessionId);
        return shoppingListService.getActiveItems(sessionId, language);
    }

    @GET
    @Path("/purchased")
    @Operation(summary = "Get purchased items")
    public List<ShoppingListItemDTO> getPurchasedItems(
            @HeaderParam("X-Session-Id") String sessionId,
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        validateSessionId(sessionId);
        return shoppingListService.getPurchasedItems(sessionId, language);
    }

    @POST
    @Path("/{dealId}")
    @Operation(summary = "Add deal to shopping list")
    public Response addToList(
            @HeaderParam("X-Session-Id") String sessionId,
            @PathParam("dealId") Long dealId,
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        validateSessionId(sessionId);
        ShoppingListItemDTO item = shoppingListService.addToList(sessionId, dealId, language);
        return Response.status(Response.Status.CREATED).entity(item).build();
    }

    @DELETE
    @Path("/{dealId}")
    @Operation(summary = "Remove deal from shopping list")
    public Response removeFromList(
            @HeaderParam("X-Session-Id") String sessionId,
            @PathParam("dealId") Long dealId
    ) {
        validateSessionId(sessionId);
        shoppingListService.removeFromList(sessionId, dealId);
        return Response.noContent().build();
    }

    @PATCH
    @Path("/{dealId}/purchased")
    @Operation(summary = "Mark item as purchased")
    public ShoppingListItemDTO markPurchased(
            @HeaderParam("X-Session-Id") String sessionId,
            @PathParam("dealId") Long dealId,
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        validateSessionId(sessionId);
        return shoppingListService.markPurchased(sessionId, dealId, language);
    }

    @DELETE
    @Path("/{dealId}/purchased")
    @Operation(summary = "Mark item as not purchased")
    public ShoppingListItemDTO markNotPurchased(
            @HeaderParam("X-Session-Id") String sessionId,
            @PathParam("dealId") Long dealId,
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        validateSessionId(sessionId);
        return shoppingListService.markNotPurchased(sessionId, dealId, language);
    }

    @DELETE
    @Operation(summary = "Clear entire shopping list")
    public Response clearList(
            @HeaderParam("X-Session-Id") String sessionId
    ) {
        validateSessionId(sessionId);
        shoppingListService.clearList(sessionId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/purchased")
    @Operation(summary = "Clear only purchased items")
    public Response clearPurchased(
            @HeaderParam("X-Session-Id") String sessionId
    ) {
        validateSessionId(sessionId);
        shoppingListService.clearPurchased(sessionId);
        return Response.noContent().build();
    }

    @GET
    @Path("/count")
    @Operation(summary = "Get item count in shopping list")
    public CountResponse getItemCount(
            @HeaderParam("X-Session-Id") String sessionId
    ) {
        validateSessionId(sessionId);
        return new CountResponse(shoppingListService.getItemCount(sessionId));
    }

    private void validateSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new BadRequestException("X-Session-Id header is required");
        }
    }

    public record CountResponse(long count) {}
}
