package be.dealfinder.resource;

import be.dealfinder.dto.CategoryDTO;
import be.dealfinder.entity.Category;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/v1/categories")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Categories", description = "Category operations")
public class CategoryResource {

    @GET
    @Operation(summary = "Get all active categories")
    public List<CategoryDTO> getCategories(
            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        return Category.findAllActive().stream()
                .map(cat -> CategoryDTO.from(cat, language))
                .collect(Collectors.toList());
    }

    @GET
    @Path("/{slug}")
    @Operation(summary = "Get category by slug")
    public Response getCategory(
            @PathParam("slug") String slug,
            
            @Parameter(description = "Language: en, nl, fr")
            @QueryParam("lang") @DefaultValue("en") String language
    ) {
        Category category = Category.findBySlug(slug);
        if (category == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(CategoryDTO.from(category, language)).build();
    }
}
