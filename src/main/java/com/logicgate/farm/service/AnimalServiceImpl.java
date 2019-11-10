package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

@Service
@Transactional
public class AnimalServiceImpl implements AnimalService {

  private final AnimalRepository animalRepository;

  private final BarnRepository barnRepository;

  @Autowired
  public AnimalServiceImpl(AnimalRepository animalRepository, BarnRepository barnRepository) {
    this.animalRepository = animalRepository;
    this.barnRepository = barnRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Animal> findAll() {
    return animalRepository.findAll();
  }

  @Override
  public void deleteAll() {
    animalRepository.deleteAll();
  }

  @Override
  public Animal addToFarm(Animal animal) {
    animalRepository.saveAndFlush(animal);
    redistributeAnimalsOfBarnColor(animal.getFavoriteColor());
    return animal;
  }

  @Override
  public void addToFarm(List<Animal> animals) {
    animals.forEach(this::addToFarm);
  }

  @Override
  public void removeFromFarm(Animal animal) {
    animalRepository.delete(animal);

    final Color barnColor = animal.getFavoriteColor();
    final List<Barn> barns = barnRepository.findByColor(barnColor);
    final List<Animal> animals = animalRepository.findByFavoriteColor(barnColor);
    if(doBarnsNeedToBeBalanced(barns, animals)){
      redistributeAnimalsOfBarnColor(animal.getFavoriteColor(), barns, animals);
    }
  }

  @Override
  public void removeFromFarm(List<Animal> animals) {
    animals.forEach(animal -> removeFromFarm(animalRepository.getOne(animal.getId())));
  }

  /**
   * This method redistributes all animals into barns.
   * @param barnColor The color of barns to redistribute the animals into
   */
  private void redistributeAnimalsOfBarnColor(final Color barnColor) {
    final List<Barn> barns = barnRepository.findByColor(barnColor);
    final List<Animal> animals = animalRepository.findByFavoriteColor(barnColor);

    redistributeAnimalsOfBarnColor(barnColor, barns, animals);
  }

    /**
   * This method redistributes all animals into barns.
   * @param barnColor The color of barns to redistribute the animals into
   * @param barns The list of barns with the same color as barnColor
   * @param animals The list of animals with the same favorite color as barnColor
   */
  private void redistributeAnimalsOfBarnColor(final Color barnColor, final List<Barn> barns, final List<Animal> animals) {
    //If there are no animals, clear out the barns
    if(animals.size() == 0){
      barnRepository.deleteAll(barns);
    } else {
      //If we don't have any barns, go ahead and create one so that we can pull the barn capacity property.
      if(barns.size() == 0){
        barns.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString() + " 0", barnColor)));
      }

      final int barnCapacity = barns.get(0).getCapacity();
      final int necessaryNumberOfBarns = findNecessaryNumberOfBarns(barnCapacity, animals.size());
      final List<Barn> availableBarns = adjustNumberOfBarns(necessaryNumberOfBarns, barns, barnColor);

      //Once the proper number of barns are established, we redistribute the animals.
      int iterator = 0;
      for (Animal animal : animals){
        Barn animalsBarn = availableBarns.get(iterator % necessaryNumberOfBarns);
        animal.setBarn(animalsBarn);
        iterator++;
      }

      animalRepository.saveAll(animals);
    }
  }


  /**
   * This method adjusts the current number of barns to match the number
   *  specified via the parameter numberOfBarns
   * @param numberOfBarns number of barns to scale to
   * @return Newly adjusted list of Barns
   */
  private List<Barn> adjustNumberOfBarns(final int numberOfBarns, final List<Barn> existingBarns, final Color barnColor){
    //Make a copy of the parameter so that we don't alter it via this function as curated list is this function's output
    final List<Barn> adjustedBarnList = new ArrayList<>(existingBarns);
    while (adjustedBarnList.size() < numberOfBarns){
      adjustedBarnList.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString(), barnColor)));
    }

    if (adjustedBarnList.size() > numberOfBarns){
      adjustedBarnList.subList(numberOfBarns, adjustedBarnList.size())
      .forEach(barnRepository::delete);
    }

    return adjustedBarnList;
  }

  /**
   * This method returns the minimum number of barns required to house
   * all of the animals.
   * @param barnCapacity The number of animals that can be housed in a barn
   * @param numberOfAnimals The number of animals to house
   * @return The minimum number of barns to house all animals within the capacity of each barn
   */
  private int findNecessaryNumberOfBarns(final int barnCapacity, final int numberOfAnimals) {
    int numberOfBarns = numberOfAnimals / barnCapacity;

    //If there was any remainder from the integer division, bump the number of barns by 1 since integer division rounds down
    if (numberOfAnimals % barnCapacity != 0)
    {
      numberOfBarns++;
    }
    return numberOfBarns;
  }

  /**
  * This method returns a boolean indicating whether or not
  *  the animals need to be redistributed amongst the barns
  * @param barns
  * @return
  */
 private boolean doBarnsNeedToBeBalanced(List<Barn> barns, List<Animal> animals) {
   boolean doBarnsNeedToBeBalanced = false;
   if(barns.size() == 0 && animals.size() != 0) {
    doBarnsNeedToBeBalanced = true;
   } else {
    final int barnCapacity = barns.get(0).getCapacity();
    final Map<Barn, Integer> barnSizeMap = new HashMap<>();

    animals.forEach(animal -> {
      Barn animalBarn = animal.getBarn();
      Integer barnSize = barnSizeMap.get(animalBarn);
      if (barnSize == null) {
        barnSize = 0;
      }

      barnSizeMap.put(animalBarn, barnSize + 1);
    });

    Integer minNumberOfAnimalsInABarn = barnSizeMap.values().stream().mapToInt(integer -> integer).min().orElse(0);
    Integer maxNumberOfAnimalsInABarn = barnSizeMap.values().stream().mapToInt(integer -> integer).max().orElse(Integer.MAX_VALUE);
    Integer sumNumberOfAnimalsInABarn = barnSizeMap.values().stream().mapToInt(integer -> integer).sum();
    Integer freeSpaceInAllBarns       = (barnCapacity * barnSizeMap.size()) - sumNumberOfAnimalsInABarn;

    if(maxNumberOfAnimalsInABarn - minNumberOfAnimalsInABarn > 1 ||
        maxNumberOfAnimalsInABarn > barnCapacity ||
        freeSpaceInAllBarns >= barnCapacity) {
      doBarnsNeedToBeBalanced = true;
    }
   }

   return doBarnsNeedToBeBalanced;
 }


}
