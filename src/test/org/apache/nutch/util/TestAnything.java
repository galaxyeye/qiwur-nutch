package org.apache.nutch.util;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.avro.util.Utf8;
import org.apache.commons.lang3.StringUtils;
import org.apache.nutch.service.model.request.SeedUrl;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.Year;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by vincent on 16-7-20.
 * Copyright @ 2013-2016 Warpspeed Information. All rights reserved
 */
public class TestAnything {

  @Test
  public void generateRegexUrlFilter() throws IOException {
    String[] files = {
        "conf/seeds/aboard.txt",
        "conf/seeds/bbs.txt",
        "conf/seeds/national.txt",
        "conf/seeds/papers.txt"
    };

    List<String> lines = Lists.newArrayList();
    for (String file : files) {
      lines.addAll(Files.readAllLines(Paths.get(file)));
    }

    Set<String> lines2 = Sets.newHashSet();
    lines.forEach(url -> {
      String pattern = StringUtils.substringBetween(url, "://", "/");
      pattern = "+http://" + pattern + "/(.+)";
      lines2.add(pattern);
    });

    Files.write(Paths.get("/tmp/regex-urlfilter.txt"), StringUtils.join(lines2, "\n").getBytes());

    System.out.println(lines2.size());
    System.out.println(StringUtils.join(lines2, ","));
  }

  @Test
  public void testUniqueSeedUrls() {
    List<SeedUrl> seedUrls = Lists.newArrayList();
    for (int i = 0; i < 10; i += 2) {
      seedUrls.add(new SeedUrl(i + 0L, "http://www.warpspeed.cn/" + i));
      seedUrls.add(new SeedUrl(i + 1L, "http://www.warpspeed.cn/" + i));
    }
    Set<SeedUrl> uniqueSeedUrls = Sets.newTreeSet(new Comparator<SeedUrl>() {
      @Override
      public int compare(SeedUrl seedUrl, SeedUrl seedUrl2) {
        return seedUrl.getUrl().compareTo(seedUrl2.getUrl());
      }
    });
    uniqueSeedUrls.addAll(seedUrls);
    uniqueSeedUrls.stream().map(SeedUrl::getUrl).forEach(System.out::println);
  }

  @Test
  public void testSystem() {
    String username = System.getenv("USER");
    System.out.println(username);
  }

  @Test
  public void normalizeUrlLists() throws IOException {
    String filename = "/home/vincent/Tmp/novel-list.txt";
    List<String> lines = Files.readAllLines(Paths.get(filename));
    Set<String> urls = Sets.newHashSet();
    Set<String> domains = Sets.newHashSet();
    Set<String> regexes = Sets.newHashSet();

    lines.stream().forEach(url -> {
      int pos = StringUtils.indexOfAny(url, "abcdefjhijklmnopqrstufwxyz");
      if (pos >= 0) {
        url = url.substring(pos);
        urls.add("http://" + url);
        domains.add(url);
        regexes.add("+http://www." + url + "(.+)");
      }
    });

    Files.write(Paths.get("/tmp/domain-urlfilter.txt"), StringUtils.join(domains, "\n").getBytes());
    Files.write(Paths.get("/tmp/novel.seeds.txt"), StringUtils.join(urls, "\n").getBytes());
    Files.write(Paths.get("/tmp/regex-urlfilter.txt"), StringUtils.join(regexes, "\n").getBytes());

    System.out.println(urls.size());
    System.out.println(StringUtils.join(urls, ","));
  }

  @Test
  public void testTreeMap() {
    final Map<Integer, String> ints = new TreeMap<>(Comparator.reverseOrder());
    ints.put(1, "1");
    ints.put(2, "2");
    ints.put(3, "3");
    ints.put(4, "4");
    ints.put(5, "5");

    System.out.println(ints.keySet().iterator().next());
  }

  @Test
  public void testTreeSet() {
    TreeSet<Integer> integers = IntStream.range(0, 30).boxed().collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    integers.forEach(System.out::print);
    integers.pollLast();
    System.out.println();
    integers.forEach(System.out::print);
  }

  @Test
  public void testOrderedStream() {
    int[] counter = {0, 0};

    TreeSet<Integer> orderedIntegers = IntStream.range(0, 1000000).boxed().collect(TreeSet::new, TreeSet::add, TreeSet::addAll);
    long startTime = System.currentTimeMillis();
    int result = orderedIntegers.stream().filter(i -> {counter[0]++; return i < 1000; }).map(i -> i * 2).reduce(0, (x, y) -> x + 2 * y);
    long endTime = System.currentTimeMillis();
    System.out.println("Compute over ordered integers, time elapsed : " + (endTime - startTime) / 1000.0 + "s");
    System.out.println("Result : " + result);

    startTime = System.currentTimeMillis();
    result = 0;
    int a = 0;
    int b = 0;
    for (Integer i : orderedIntegers) {
      if (i < 1000) {
        b = i;
        b *= 2;
        result += a + 2 * b;
      }
      else {
        break;
      }
    }
    endTime = System.currentTimeMillis();
    System.out.println("Compute over ordered integers, handy code, time elapsed : " + (endTime - startTime) / 1000.0 + "s");
    System.out.println("Result : " + result);

    List<Integer> unorderedIntegers = IntStream.range(0, 1000000).boxed().collect(Collectors.toList());
    startTime = System.currentTimeMillis();
    result = unorderedIntegers.stream().filter(i -> {counter[1]++; return i < 1000; }).map(i -> i * 2).reduce(0, (x, y) -> x + 2 * y);
    endTime = System.currentTimeMillis();
    System.out.println("Compute over unordered integers, time elapsed : " + (endTime - startTime) / 1000.0 + "s");
    System.out.println("Result : " + result);

    System.out.println("Filter loops : " + counter[0] + ", " + counter[1]);
  }

  @Test
  public void testDateTime() {
    Instant t1 = Instant.EPOCH;
    Instant t2 = Instant.now();

    long days = ChronoUnit.DAYS.between(t1, t2);
    System.out.println(days);

    System.out.println(Duration.ofDays(365 * 100).getSeconds());

    System.out.println(Duration.ofMinutes(60).toMillis());
  }

  @Test
  public void testUtf8() {
    String s = "";
    Utf8 u = new Utf8(s);
    System.out.println(u.length());
    System.out.println(u.toString());
  }

  @Test
  public void testAtomic() {
    AtomicInteger counter = new AtomicInteger(100);
    int deleted = 10;
    counter.addAndGet(-deleted);
    System.out.println(counter);
  }
}
