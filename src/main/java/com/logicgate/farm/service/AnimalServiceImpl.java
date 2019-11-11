package com.logicgate.farm.service;

import com.logicgate.farm.domain.Animal;
import com.logicgate.farm.domain.Barn;
import com.logicgate.farm.domain.Color;
import com.logicgate.farm.repository.AnimalRepository;
import com.logicgate.farm.repository.BarnRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    final Color barnColor = animal.getFavoriteColor();
    final List<Barn> barns = barnRepository.findByColor(barnColor);
    final List<Animal> animals = animalRepository.findByFavoriteColor(barnColor);


    animalRepository.saveAndFlush(animal);
    if (barns.size() == 0) {
      barns.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString() + " 0", barnColor)));
    }
    animal.setBarn(barns.get(0));

    animals.add(animal);
    redistributeAnimalsOfBarnColor(barnColor, barns, animals);
    return animal;
  }

  @Override
  public void addToFarm(List<Animal> animals) {
    animals.forEach(this::addToFarm);
  }

  @Override
  public void removeFromFarm(Animal animal) {
    animalRepository.delete(animal);
    redistributeAnimalsOfBarnColor(animal.getFavoriteColor());
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
  private void redistributeAnimalsOfBarnColor(final Color barnColor,
                                              final List<Barn> barns,
                                              final List<Animal> animals) {
    //If there are no animals, clear out the barns
    if (animals.size() == 0) {
      barnRepository.deleteAll(barns);
    } else {
      //If we don't have any barns, go ahead and create one so that we can pull the barn capacity property.
      if (barns.size() == 0) {
        barns.add(barnRepository.saveAndFlush(new Barn("Barn " + barnColor.toString() + " 0", barnColor)));
      }

      final int barnCapacity = barns.get(0).getCapacity();
      final int necessaryNumberOfBarns = findNecessaryNumberOfBarns(barnCapacity, animals.size());
      final int animalsPerBarn = animals.size() / necessaryNumberOfBarns;
      final int remainderAnimals = animals.size() % necessaryNumberOfBarns;
      final List<Animal> animalsToRehome = new ArrayList<>();
      final Map<Barn, List<Animal>> barnToAnimalRelationMap = animals.stream()
          .collect(Collectors.groupingBy(Animal::getBarn));

      //Adjust the number of barns
      while (barnToAnimalRelationMap.size() < necessaryNumberOfBarns) {
        barnToAnimalRelationMap.put(barnRepository
                                    .saveAndFlush(new Barn("Barn " + barnColor.toString(),
                                                            barnColor)),
                                                  new ArrayList<>());
      }

      if (barnToAnimalRelationMap.size() > necessaryNumberOfBarns) {
        barnToAnimalRelationMap.keySet().stream().collect(Collectors.toList())
            .subList(necessaryNumberOfBarns, barnToAnimalRelationMap.size()).forEach(barn -> {
              barnRepository.delete(barn);
              animalsToRehome.addAll(barnToAnimalRelationMap.get(barn));
              barnToAnimalRelationMap.remove(barn);
            });
      }

      //Pull all the extra animals out of barns with too many animals.
      barnToAnimalRelationMap.entrySet().stream()
          .filter((entry) -> {
            return (entry.getValue().size() > animalsPerBarn);
          })
          .forEach(entry -> {
            final List<Animal> animalsInBarn = entry.getValue();
            final List<Animal> overflowAnimals = animalsInBarn.subList(animalsPerBarn, animalsInBarn.size());
            animalsToRehome.addAll(overflowAnimals);
          });

      //Put the extra animals into barns with too few animals.
      barnToAnimalRelationMap.entrySet().stream()
          .filter((entry) -> {
            return (entry.getValue().size() < animalsPerBarn);
          })
          .forEach(entry -> {
            final Barn barn = entry.getKey();
            final List<Animal> animalsInBarn = entry.getValue();
            final List<Animal> newAnimals = animalsToRehome.subList(0, animalsPerBarn - animalsInBarn.size());
            newAnimals.forEach(animal -> {
              animal.setBarn(barn);
            });
            //Save all of the animals added to this barn in this foreach
            animalRepository.saveAll(newAnimals);
            animalsToRehome.removeAll(newAnimals);
          });

      //For any remaing animals, put each one in a barn.
      List<Barn> barnsForRemainingAnimals = new ArrayList<>(barnToAnimalRelationMap.keySet())
                                                              .subList(0, remainderAnimals);
      barnsForRemainingAnimals.forEach(barn -> {
        animalsToRehome.get(barnsForRemainingAnimals.indexOf(barn)).setBarn(barn);
      });

      //Save the animals that got a home due to being remainders.
      animalRepository.saveAll(animalsToRehome);
    }
  }

  /**
   * This method provides the minimum number of barns required to house
   *  all of the animals.
   * @param barnCapacity The number of animals that can be housed in a barn
   * @param numberOfAnimals The number of animals to house
   * @return The minimum number of barns to house all animals within the capacity of each barn
   */
  private int findNecessaryNumberOfBarns(final int barnCapacity, final int numberOfAnimals) {
    int numberOfBarns = numberOfAnimals / barnCapacity;
    int remainderOfAnimals = numberOfAnimals % barnCapacity;
    if (remainderOfAnimals > 0) {
      numberOfBarns++;
    }

    return numberOfBarns;
  }
}
