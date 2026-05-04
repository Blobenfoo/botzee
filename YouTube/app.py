from flask import Flask, render_template, request, redirect, flash, session
import base64
import json, hashlib

app = Flask(__name__)
app.secret_key = "youtubesucks"
with open("videodata.json") as f:
    video__data = json.load(f)
with open("latestuploads.json") as f:
    latest_uploads = json.load(f)
with open("creators.json") as f:
    creator_data = json.load(f)
with open("passwords.json") as f:
    passwords = json.load(f)

@app.route('/watch/<name>')
def watch_video(name):

    video__data[name]["views"] += 1
    # Read the MP4 file and encode to base64
    with open(f'videos/{name}', 'rb') as f:
        video_bytes = f.read()
        video_data = base64.b64encode(video_bytes).decode('utf-8')
    
    with open("videodata.json", "w") as f:
        json.dump(video__data, f)

    #print(video__data[name])

    return render_template('app.htm',
        video_data=video_data,
        video_title=video__data[name]["video_title"],
        views=video__data[name]["views"],
        #upload_date='2 days ago',
        # #likes='2.4K',
        
        likes = video__data[name]["Likes"],
        channel_name=video__data[name]["creator"],
        subscribers=creator_data[video__data[name]["creator"]]["subscribers"],
        description=video__data[name]["description"])

@app.route('/addNew/', methods=["GET","POST"])
def addNew():
    if "loggedIn" not in session:
        return redirect("/ChannelLogin/") 
    if request.method == 'POST':
        #print(request.files.keys())
        if 'file' not in request.files:
            flash('No file part')
            return redirect(request.url)
        video__data[request.files["file"].filename] = {"views":0,"description":request.form["Description"],"video_title":request.form["Title"],"Likes":0,"creator":session["loggedIn"]}
        file = request.files["file"]
        with open(f"videos/{file.filename}", "w") as f:

            file.save(f"videos/{file.filename}")
        with open("videodata.json", "w") as f:
            json.dump(video__data, f)
        if len(latest_uploads) >= 20:
            latest_uploads.pop(0)
        latest_uploads.append({"name":file.filename,"title":request.form["Title"],"creator":session.get("loggedIn"),"Likes":0})
        with open("latestuploads.json", "w") as f:
            json.dump(latest_uploads, f)
        return redirect(f"/watch/{file.filename}")
    return render_template("newVid.htm")

@app.route('/ChannelLogin/', methods=['GET','POST'])
def ChannelLogin():
    if request.method == 'POST':
        hashed_pwd = passwords[request.form["channelName"]]
        print(f"{hashlib.sha256(request.form["pwd"].encode())} != {hashed_pwd}?!?!")
        if hashlib.sha256(request.form["pwd"].encode()).hexdigest() == hashed_pwd:
            session["loggedIn"] = request.form["channelName"]
            return redirect("/")
        flash("Invalid Channel Name/Password")
        return redirect("/ChannelLogin/")
    return render_template("login.htm")

@app.route('/LikeVideo/<name>')
def LikeVideo(name):
    video__data[name]["Likes"] += 1
    with open("videodata.json", "w") as f:
            json.dump(video__data, f)
    return redirect("")

@app.route('/subscribe/<name>')
def Subscribe(name):
    if "loggedIn" in session:
        if name not in creator_data[session["loggedIn"]]["subscribed"]:
            creator_data[name]["subscribers"] += 1
            creator_data[name]["subscribees"].append(session["loggedIn"])
            creator_data[session["loggedIn"]]["subscribed"].append(name)
            with open("creators.json", "w") as f:
                json.dump(creator_data, f)
            return redirect("/")
        flash("You've already subscribed to this creator!")
        return redirect("/")
    else: return redirect(f"/subscribeLogin/{name}")

@app.route('/subscribeLogin/<name>', methods=['GET','POST'])
def subscribeLogin(name):
    if request.method == 'POST':
        hashed_pwd = passwords[request.form["channelName"]]
        if hashlib.sha256(request.form["pwd"].encode()).hexdigest() == hashed_pwd:
            session["loggedIn"] = request.form["channelName"]
            return redirect(f"/subscribe/{name}")
        flash("Invalid name/password")
        return redirect(f"/subscribeLogin/{name}")
    return render_template("login.htm")

@app.route("/newChannel/", methods=["GET","POST"])
def newChannel():
    if request.method == 'POST':
        if request.form["channelName"] not in creator_data:
            hashed_pwd = hashlib.sha256(request.form["pwd"].encode()).hexdigest()
            passwords[request.form["channelName"]] = hashed_pwd
            creator_data[request.form["channelName"]] = {"subscribers":0,"subscribed":[],"subscribees":[]}
            with open("passwords.json", "w") as f:
                json.dump(passwords, f)
            with open("creators.json", "w") as f:
                json.dump(creator_data, f)
            return redirect("/")
        flash("Channel name was already taken.")
        return redirect("/newChannel")
    return render_template("newChannel.html")

@app.route("/")
def main():
    return render_template("home.htm", latest=latest_uploads[::-1], other_data=video__data)

