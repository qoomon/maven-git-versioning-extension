package me.qoomon.gitversioning.commons;

import java.security.SecureRandom;
import java.util.function.Supplier;

public class RandomNumberSupplier {
  private final SecureRandom generator;

  public RandomNumberSupplier() {
    this(new SecureRandom());
  }

  RandomNumberSupplier(final SecureRandom generator) {
    this.generator = generator;
  }

  public Supplier<String> randomFourDigits() {
    return this::fourDigits;
  }

  public Supplier<String> randomFiveDigits() {
    return this::fiveDigits;
  }

  public Supplier<String> randomSixDigits() {
    return this::sixDigits;
  }

  private String fourDigits() {
    return String.format("%04d", generator.nextInt(10000));
  }

  private String fiveDigits() {
    return String.format("%05d", generator.nextInt(100000));
  }

  private String sixDigits() {
    return String.format("%06d", generator.nextInt(1000000));
  }
}
