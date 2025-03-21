package com.aucontraire.gmailbuddy.mapper;

import com.aucontraire.gmailbuddy.dto.FilterCriteriaDTO;
import com.google.api.services.gmail.model.FilterCriteria;
import org.springframework.stereotype.Component;

@Component
public class FilterCriteriaMapper {

    public FilterCriteria toFilterCriteria(FilterCriteriaDTO dto) {
        if (dto == null) {
            return null;
        }
        FilterCriteria criteria = new FilterCriteria();
        criteria.setFrom(dto.getFrom());
        criteria.setTo(dto.getTo());
        criteria.setSubject(dto.getSubject());
        criteria.setHasAttachment(dto.getHasAttachment());
        criteria.setQuery(dto.getQuery());
        criteria.setNegatedQuery(dto.getNegatedQuery());
        return criteria;
    }

    public FilterCriteriaDTO toDTO(FilterCriteria criteria) {
        if (criteria == null) {
            return null;
        }
        FilterCriteriaDTO dto = new FilterCriteriaDTO();
        dto.setFrom(criteria.getFrom());
        dto.setTo(criteria.getTo());
        dto.setSubject(criteria.getSubject());
        dto.setHasAttachment(criteria.getHasAttachment());
        dto.setQuery(criteria.getQuery());
        dto.setNegatedQuery(criteria.getNegatedQuery());
        return dto;
    }
}
