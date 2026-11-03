package com.childnutrition.service;

import com.childnutrition.entity.Child;
import com.childnutrition.repository.ChildRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ChildAccessService {
    private final ChildRepository children;
    public ChildAccessService(ChildRepository children) { this.children = children; }

    public List<Child> list(String email, boolean admin) {
        return admin ? children.findAll() : children.findByParentEmailOrderByNameAsc(email);
    }

    public Child lockForTargetChange(Long childId) {
        return children.findByIdForUpdate(childId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child not found"));
    }

    public Child require(Long id, Authentication authentication) {
        if (id == null || id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Child ID must be a positive number.");
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return (admin ? children.findById(id) : children.findByIdAndParentEmail(id, authentication.getName()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Child not found"));
    }
}
