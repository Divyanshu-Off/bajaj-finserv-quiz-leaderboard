# Bajaj Finserv Health — Quiz Leaderboard System

> **Internship Assignment** | Java Qualifier | SRM — April 2024

A self-contained Java solution that polls a quiz validator API, deduplicates responses, computes a ranked leaderboard, and submits it — all with **zero external dependencies**.

---

## Problem Summary

The validator API simulates a quiz show where participants earn scores across multiple rounds. The same event data may appear in more than one poll response. The goal is to:

1. Poll the API **10 times** (poll indices `0` – `9`) with a **5-second delay** between each call.
2. **Deduplicate** events using the composite key `roundId + participant`.
3. **Aggregate** the total score per participant.
4. **Sort** the leaderboard by `totalScore` descending.
5. **Submit** the final leaderboard exactly once.

---

## Project Structure

```
bajaj-finserv-quiz-leaderboard/
├── src/
│   └── QuizLeaderboard.java   # Single-file solution (no build tool needed)
├── .gitignore
├── LICENSE
└── README.md
```

---

## Prerequisites

| Requirement | Version  |
|-------------|----------|
| Java JDK    | 11 or higher |

No Maven, Gradle, or third-party libraries required.

---

## Setup

**1. Clone the repository**
```bash
git clone https://github.com/Divyanshu-Off/bajaj-finserv-quiz-leaderboard.git
cd bajaj-finserv-quiz-leaderboard
```

**2. Set your registration number**

Open `src/QuizLeaderboard.java` and update line 22:
```java
private static final String REG_NO = "2024CS101"; // <-- replace with your reg number
```

---

## How to Run

**Compile**
```bash
javac src/QuizLeaderboard.java -d out
```

**Run**
```bash
java -cp out QuizLeaderboard
```

> The program takes approximately **45 seconds** to complete due to the mandatory 5-second delay between polls.

---

## How It Works

### Step 1 — Poll (10 times)
```
GET /quiz/messages?regNo=<REG_NO>&poll=0
GET /quiz/messages?regNo=<REG_NO>&poll=1
...
GET /quiz/messages?regNo=<REG_NO>&poll=9
```
A 5-second delay is enforced between each request.

### Step 2 — Deduplicate
Each event is identified by a composite key: `"roundId|participant"`.  
`putIfAbsent` is used so only the **first occurrence** of each key is kept — duplicates are silently dropped.

### Step 3 — Aggregate
Scores for the same participant across different rounds are summed.

### Step 4 — Sort
The leaderboard is sorted by `totalScore` in **descending order**.

### Step 5 — Submit (once)
```
POST /quiz/submit
Content-Type: application/json

{
  "regNo": "...",
  "leaderboard": [
    { "participant": "Alice", "totalScore": 100 },
    { "participant": "Bob",   "totalScore": 80  }
  ]
}
```

---

## Sample Output

```
==============================================
  Bajaj Finserv Health - Quiz Leaderboard
==============================================
Registration No : 2024CS101
Total Polls     : 10

Poll 0 ... OK
  Waiting 5 seconds...
Poll 1 ... OK
...
Poll 9 ... OK

Unique events after deduplication: 12

==============================================
  LEADERBOARD
==============================================
Rank   Participant          Total Score
----------------------------------------------
1      Alice                100
2      Bob                  80
----------------------------------------------
Combined Total Score: 180
==============================================

Submitting...
Response: {"isCorrect":true,"isIdempotent":true,"submittedTotal":180,"expectedTotal":180,"message":"Correct!"}
```

---

## API Reference

**Base URL:** `https://devapigw.vidalhealthtpa.com/srm-quiz-task`

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/quiz/messages?regNo={regNo}&poll={0-9}` | Fetch events for a given poll index |
| POST | `/quiz/submit` | Submit the final leaderboard |

---

## Key Design Decisions

| Decision | Reason |
|----------|--------|
| Single `.java` file | No build tool dependency; runs anywhere with JDK 11+ |
| `putIfAbsent` for dedup | Simple, correct — first occurrence wins, rest discarded |
| No JSON library | Uses lightweight string parsing to avoid external deps |
| Submit exactly once | Satisfies `isIdempotent: true` requirement |

---

## Author

**Divyanshu Singh**  
B.Tech — Computational and Data Science  
Indian Institute of Science, Bangalore
