package be.dealfinder.resource;

import be.dealfinder.dto.RetailerDTO;
import be.dealfinder.entity.Retailer;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/retailers")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Retailers", description = "Retailer operations")
public class RetailerResource {

    @GET
    @Operation(summary = "Get all active retailers")
    public List<RetailerDTO> getRetailers() {
        return Retailer.findAllActive().stream()
                .map(RetailerDTO::from)
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{slug}")
    @Operation(summary = "Get retailer by slug")
    public Response getRetailer(@PathParam("slug") String slug) {
        Retailer retailer = Retailer.findBySlug(slug);
        if (retailer == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(RetailerDTO.from(retailer)).build();
    }
}
