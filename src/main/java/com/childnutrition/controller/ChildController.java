package com.childnutrition.controller;

import com.childnutrition.dto.ChildDtos;
import com.childnutrition.entity.Child;
import com.childnutrition.repository.ChildRepository;
import com.childnutrition.repository.UserRepository;
import com.childnutrition.service.ChildAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/children")
public class ChildController {
    private final ChildRepository children;
    private final UserRepository users;
    private final ChildAccessService access;
    public ChildController(ChildRepository children, UserRepository users, ChildAccessService access) {
        this.children = children; this.users = users; this.access = access;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ChildDtos.Response create(@Valid @RequestBody ChildDtos.Request request, Authentication auth) {
        Child child = new Child(); child.setParent(users.findByEmail(auth.getName()).orElseThrow());
        apply(child, request); return ChildDtos.Response.from(children.save(child));
    }

    @GetMapping public List<ChildDtos.Response> list(Authentication auth) {
        return access.list(auth.getName(), isAdmin(auth)).stream().map(ChildDtos.Response::from).toList();
    }

    @GetMapping("/{id}") public ChildDtos.Response get(@PathVariable Long id, Authentication auth) {
        return ChildDtos.Response.from(access.require(id, auth));
    }

    @PutMapping("/{id}") public ChildDtos.Response update(@PathVariable Long id,
            @Valid @RequestBody ChildDtos.Request request, Authentication auth) {
        Child child = access.require(id, auth); apply(child, request); return ChildDtos.Response.from(children.save(child));
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) { children.delete(access.require(id, auth)); }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    private static void apply(Child child, ChildDtos.Request request) {
        child.setName(request.name().trim()); child.setDateOfBirth(request.dateOfBirth()); child.setGender(request.gender().trim());
        child.setAllergies(request.allergies() == null ? null : request.allergies().trim());
        child.setDietaryPreferences(request.dietaryPreferences() == null ? null : request.dietaryPreferences().trim());
    }
}
