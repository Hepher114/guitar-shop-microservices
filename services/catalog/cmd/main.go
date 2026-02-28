package main

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"regexp"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"
)

type Product struct {
	ID          int     `json:"id"`
	Name        string  `json:"name"`
	Description string  `json:"description"`
	Price       float64 `json:"price"`
	Category    string  `json:"category"`
	Brand       string  `json:"brand"`
	Stock       int     `json:"stock"`
	ImageURL    string  `json:"image_url"`
	CreatedAt   string  `json:"created_at"`
}

type Category struct {
	ID   int    `json:"id"`
	Name string `json:"name"`
	Slug string `json:"slug"`
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}

type routeEntry struct {
	method  string
	pattern *regexp.Regexp
	params  []string
	handler func(http.ResponseWriter, *http.Request, map[string]string)
}

var routeTable []routeEntry

func addRoute(method, path string, h func(http.ResponseWriter, *http.Request, map[string]string)) {
	var params []string
	re := regexp.MustCompile(`\{(\w+)\}`)
	pattern := re.ReplaceAllStringFunc(path, func(m string) string {
		params = append(params, m[1:len(m)-1])
		return `([^/]+)`
	})
	routeTable = append(routeTable, routeEntry{method, regexp.MustCompile(`^` + pattern + `$`), params, h})
}

func dispatch(w http.ResponseWriter, r *http.Request) {
	for _, rt := range routeTable {
		if rt.method != r.Method {
			continue
		}
		m := rt.pattern.FindStringSubmatch(r.URL.Path)
		if m == nil {
			continue
		}
		vars := make(map[string]string)
		for i, name := range rt.params {
			vars[name] = m[i+1]
		}
		rt.handler(w, r, vars)
		return
	}
	http.NotFound(w, r)
}

var db *sql.DB

func connectDB() {
	dsn := fmt.Sprintf("%s:%s@tcp(%s:%s)/%s?parseTime=true",
		getEnv("DB_USER", "guitarshop"),
		getEnv("DB_PASSWORD", "guitarshop123"),
		getEnv("DB_HOST", "catalog-db"),
		getEnv("DB_PORT", "3306"),
		getEnv("DB_NAME", "guitarshop_catalog"),
	)
	for i := 0; i < 15; i++ {
		var err error
		db, err = sql.Open("mysql", dsn)
		if err == nil {
			if db.Ping() == nil {
				log.Println("✅ Connected to catalog DB")
				return
			}
		}
		log.Printf("⏳ Waiting for DB... %d/15", i+1)
		time.Sleep(4 * time.Second)
	}
	log.Fatal("❌ Could not connect to catalog DB")
}

func seedDB() {
	db.Exec(`CREATE TABLE IF NOT EXISTS products (
		id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(255) NOT NULL,
		description TEXT, price DECIMAL(10,2), category VARCHAR(100),
		brand VARCHAR(100), stock INT DEFAULT 0, image_url VARCHAR(500),
		created_at DATETIME DEFAULT CURRENT_TIMESTAMP)`)
	db.Exec(`CREATE TABLE IF NOT EXISTS categories (
		id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL,
		slug VARCHAR(100) NOT NULL UNIQUE)`)

	var n int
	db.QueryRow("SELECT COUNT(*) FROM categories").Scan(&n)
	if n == 0 {
		for _, c := range [][2]string{
			{"Electric Guitars", "electric-guitars"}, {"Acoustic Guitars", "acoustic-guitars"},
			{"Bass Guitars", "bass-guitars"}, {"Amplifiers", "amplifiers"},
			{"Effects Pedals", "effects-pedals"}, {"Accessories", "accessories"},
		} {
			db.Exec("INSERT INTO categories (name,slug) VALUES (?,?)", c[0], c[1])
		}
	}
	db.QueryRow("SELECT COUNT(*) FROM products").Scan(&n)
	if n == 0 {
		for _, p := range [][]any{
			{"Fender Stratocaster Player", "Classic American electric with three single-coil pickups.", 749.99, "Electric Guitars", "Fender", 15, "/images/fender-strat.svg"},
			{"Gibson Les Paul Standard", "Iconic mahogany body with maple top and humbuckers.", 2499.00, "Electric Guitars", "Gibson", 8, "/images/gibson-les-paul.svg"},
			{"Taylor 214ce Grand Auditorium", "Versatile acoustic-electric with rosewood back.", 1199.00, "Acoustic Guitars", "Taylor", 12, "/images/taylor-214ce.svg"},
			{"Martin D-28 Dreadnought", "The benchmark acoustic with Sitka spruce top.", 3099.00, "Acoustic Guitars", "Martin", 5, "/images/martin-d28.svg"},
			{"Fender Precision Bass", "The bass guitar that started it all.", 849.99, "Bass Guitars", "Fender", 10, "/images/fender-pbass.svg"},
			{"Fender Blues Junior IV", "15-watt all-tube combo amp.", 599.99, "Amplifiers", "Fender", 20, "/images/blues-junior.svg"},
			{"Boss DS-1 Distortion Pedal", "World's best-selling distortion pedal since 1978.", 59.99, "Effects Pedals", "Boss", 50, "/images/boss-ds1.svg"},
			{"Ernie Ball Regular Slinky", "The most popular electric guitar strings.", 6.99, "Accessories", "Ernie Ball", 200, "/images/ernie-ball.svg"},
			{"Dunlop Tortex Pick Pack", "Industry-standard picks in multiple gauges.", 4.99, "Accessories", "Dunlop", 300, "/images/dunlop-picks.svg"},
			{"Ibanez RG550 Genesis", "High-performance Japanese electric with edge tremolo.", 999.99, "Electric Guitars", "Ibanez", 7, "/images/ibanez-rg550.svg"},
		} {
			db.Exec("INSERT INTO products (name,description,price,category,brand,stock,image_url) VALUES (?,?,?,?,?,?,?)",
				p[0], p[1], p[2], p[3], p[4], p[5], p[6])
		}
		log.Println("✅ Products seeded")
	}
}

func healthHandler(w http.ResponseWriter, r *http.Request, _ map[string]string) {
	writeJSON(w, 200, map[string]string{"status": "UP", "service": "guitarshop-catalog"})
}

func listProductsHandler(w http.ResponseWriter, r *http.Request, _ map[string]string) {
	q := r.URL.Query()
	query := "SELECT id,name,description,price,category,brand,stock,image_url,created_at FROM products WHERE 1=1"
	var args []any
	if c := q.Get("category"); c != "" {
		query += " AND category=?"
		args = append(args, c)
	}
	if s := q.Get("search"); s != "" {
		query += " AND (name LIKE ? OR description LIKE ? OR brand LIKE ?)"
		p := "%" + s + "%"
		args = append(args, p, p, p)
	}
	query += " LIMIT 50"
	rows, err := db.Query(query, args...)
	if err != nil {
		writeJSON(w, 500, map[string]string{"error": "DB error"})
		return
	}
	defer rows.Close()
	var products []Product
	for rows.Next() {
		var p Product
		var ca []byte
		rows.Scan(&p.ID, &p.Name, &p.Description, &p.Price, &p.Category, &p.Brand, &p.Stock, &p.ImageURL, &ca)
		p.CreatedAt = string(ca)
		products = append(products, p)
	}
	if products == nil {
		products = []Product{}
	}
	writeJSON(w, 200, products)
}

func getProductHandler(w http.ResponseWriter, r *http.Request, vars map[string]string) {
	var p Product
	var ca []byte
	err := db.QueryRow(
		"SELECT id,name,description,price,category,brand,stock,image_url,created_at FROM products WHERE id=?", vars["id"],
	).Scan(&p.ID, &p.Name, &p.Description, &p.Price, &p.Category, &p.Brand, &p.Stock, &p.ImageURL, &ca)
	if err == sql.ErrNoRows {
		writeJSON(w, 404, map[string]string{"error": "Not found"})
		return
	} else if err != nil {
		writeJSON(w, 500, map[string]string{"error": "DB error"})
		return
	}
	p.CreatedAt = string(ca)
	writeJSON(w, 200, p)
}

func listCategoriesHandler(w http.ResponseWriter, r *http.Request, _ map[string]string) {
	rows, err := db.Query("SELECT id,name,slug FROM categories")
	if err != nil {
		writeJSON(w, 500, map[string]string{"error": "DB error"})
		return
	}
	defer rows.Close()
	var cats []Category
	for rows.Next() {
		var c Category
		rows.Scan(&c.ID, &c.Name, &c.Slug)
		cats = append(cats, c)
	}
	if cats == nil {
		cats = []Category{}
	}
	writeJSON(w, 200, cats)
}

func main() {
	connectDB()
	seedDB()

	addRoute("GET", "/health", healthHandler)
	addRoute("GET", "/products", listProductsHandler)
	addRoute("GET", "/products/{id}", getProductHandler)
	addRoute("GET", "/categories", listCategoriesHandler)

	port := getEnv("PORT", "8080")
	log.Printf("🎸 GuitarShop Catalog Service running on :%s", port)
	log.Fatal(http.ListenAndServe(":"+port, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Origin", "*")
		r.URL.Path = strings.TrimRight(r.URL.Path, "/")
		if r.URL.Path == "" {
			r.URL.Path = "/"
		}
		dispatch(w, r)
	})))
}
