package com.logicgate.farm.repository;

import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BarnRepository extends JpaRepository<Barn, Long> {

  /**
   * This method finds all barns with a color equal to that color passed
   *  in as the parameter color. This method is filled out using JPA
   *  Query creation.
   *
   * @param color Barn's color
   * @return List of barns with a color equal to the parameter color
   */
  public List<Barn> findByColor(Color color);

}
